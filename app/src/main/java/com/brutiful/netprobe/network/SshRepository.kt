package com.brutiful.netprobe.network

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.brutiful.netprobe.model.SshAuthMethod
import com.brutiful.netprobe.model.SshConnectionConfig
import com.brutiful.netprobe.model.SshConnectionState
import com.brutiful.netprobe.model.SshPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.OutputStream
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.PublicKey
import java.security.Security

class SshRepository(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "ssh_host_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var shellOutputStream: OutputStream? = null

    suspend fun startShellSession(
        config: SshConnectionConfig,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onStateChange: (SshConnectionState) -> Unit,
        onHostKeyVerification: (String, String, String, (Boolean) -> Unit) -> Unit
    ) = withContext(Dispatchers.IO) {
        disconnect()

        val tag = "SshRepository"
        
        // Log crypto provider state right before connection
        val bcProvider = Security.getProvider("BC")
        if (bcProvider != null) {
            Log.i(tag, "Using BC Provider for SSH: ${bcProvider.name} v${bcProvider.version} - ${bcProvider.javaClass.name}")
        } else {
            Log.e(tag, "CRITICAL: BC Provider NOT FOUND in Security context!")
        }

        val currentClient = SSHClient()
        client = currentClient
        
        try {
            currentClient.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                    val fingerprint = net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
                    val storedFingerprint = sharedPrefs.getString(hostname, null)

                    if (storedFingerprint == fingerprint) return true
                    
                    val deferred = CompletableDeferred<Boolean>()
                    
                    onHostKeyVerification(hostname, fingerprint, key.algorithm) { accepted ->
                        if (accepted) {
                            sharedPrefs.edit().putString(hostname, fingerprint).apply()
                        }
                        deferred.complete(accepted)
                    }
                    
                    return runBlocking {
                        withTimeoutOrNull(30000) { deferred.await() } ?: false
                    }
                }

                override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
                    return emptyList()
                }
            })

            onStateChange(SshConnectionState.ResolvingHost)
            onStateChange(SshConnectionState.Connecting)
            try {
                currentClient.connect(config.host, config.port)
            } catch (e: UnknownHostException) {
                onStateChange(SshConnectionState.Error("Failed to resolve host: ${config.host}", "${config.username}@${config.host}", SshPhase.DNS))
                return@withContext
            } catch (e: ConnectException) {
                onStateChange(SshConnectionState.Error("TCP connect failed to ${config.host}:${config.port}", "${config.username}@${config.host}", SshPhase.Connection))
                return@withContext
            }

            onStateChange(SshConnectionState.Authenticating)
            try {
                when (val auth = config.authMethod) {
                    is SshAuthMethod.Password -> currentClient.authPassword(config.username, auth.password)
                    is SshAuthMethod.KeyPair -> {
                        val keyProvider = currentClient.loadKeys(auth.privateKey, auth.passphrase)
                        currentClient.authPublickey(config.username, keyProvider)
                    }
                }
            } catch (e: UserAuthException) {
                val allowed = currentClient.userAuth.allowedMethods.joinToString(", ")
                val message = if (currentClient.userAuth.allowedMethods.contains("publickey") && !currentClient.userAuth.allowedMethods.contains("password")) {
                    "Server does not allow password authentication (Allows: $allowed)"
                } else {
                    "Authentication failed for ${config.username}"
                }
                onStateChange(SshConnectionState.Error(message, "${config.username}@${config.host}", SshPhase.Authentication))
                return@withContext
            }

            onStateChange(SshConnectionState.OpeningShell)
            val currentSession = currentClient.startSession()
            session = currentSession
            
            currentSession.allocatePTY("xterm", 80, 24, 0, 0, emptyMap())
            val currentShell = try {
                currentSession.startShell()
            } catch (e: Exception) {
                onStateChange(SshConnectionState.Error("Failed to open interactive shell", "${config.username}@${config.host}", SshPhase.Session))
                return@withContext
            }
            
            shell = currentShell
            shellOutputStream = currentShell.outputStream

            currentClient.connection.keepAlive.keepAliveInterval = 30
            
            onStateChange(SshConnectionState.Connected("${config.username}@${config.host}:${config.port}"))

            val inputReader = currentShell.inputStream.bufferedReader()
            val errorReader = currentShell.errorStream.bufferedReader()

            val errorJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val buffer = CharArray(1024)
                    var read: Int
                    while (errorReader.read(buffer).also { read = it } != -1) {
                        onError(String(buffer, 0, read))
                    }
                } catch (e: Exception) {}
            }

            try {
                val buffer = CharArray(1024)
                var read: Int
                while (inputReader.read(buffer).also { read = it } != -1) {
                    onOutput(String(buffer, 0, read))
                }
            } catch (e: Exception) {
            } finally {
                errorJob.cancel()
                onStateChange(SshConnectionState.Disconnected)
            }

        } catch (e: TransportException) {
            val cause = e.cause
            val message = if (cause is NoSuchAlgorithmException || cause is NoSuchProviderException) {
                "Crypto configuration error: ${cause.message}. Ensure BouncyCastle is correctly registered."
            } else {
                "SSH transport error: ${e.message}"
            }
            Log.e(tag, message, e)
            onStateChange(SshConnectionState.Error(message, "${config.username}@${config.host}", SshPhase.Connection))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in SSH session", e)
            onStateChange(SshConnectionState.Error("Unexpected error: ${e.message}", "${config.username}@${config.host}", SshPhase.General))
        } finally {
            disconnect()
        }
    }

    suspend fun writeToShell(command: String) = withContext(Dispatchers.IO) {
        shellOutputStream?.let {
            it.write((command + "\n").toByteArray())
            it.flush()
        }
    }

    suspend fun updateWindowDimensions(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        try {
            shell?.changeWindowDimensions(cols, rows, 0, 0)
        } catch (e: Exception) {
            Log.e("SshRepository", "Failed to resize PTY: ${e.message}")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            shell?.close()
            session?.close()
            client?.disconnect()
        } catch (e: Exception) {
        } finally {
            shell = null
            session = null
            client = null
            shellOutputStream = null
        }
    }
}
