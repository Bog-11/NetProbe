package com.brutiful.netprobe.model

sealed class SshAuthMethod {
    data class Password(val password: String) : SshAuthMethod()
    data class KeyPair(val privateKey: String, val passphrase: String? = null) : SshAuthMethod()
}

data class SshConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: SshAuthMethod
)

sealed class SshConnectionState {
    object Disconnected : SshConnectionState()
    object ResolvingHost : SshConnectionState()
    object Connecting : SshConnectionState()
    object Authenticating : SshConnectionState()
    object OpeningShell : SshConnectionState()
    data class Connected(val target: String) : SshConnectionState()
    data class Error(val message: String, val target: String? = null, val phase: SshPhase = SshPhase.General) : SshConnectionState()
    data class HostKeyVerificationRequired(
        val hostname: String,
        val fingerprint: String,
        val keyType: String,
        val onAccept: () -> Unit,
        val onReject: () -> Unit
    ) : SshConnectionState()
}

enum class SshPhase {
    General,
    DNS,
    Connection,
    Authentication,
    Session
}

sealed class TerminalLine {
    data class SystemMessage(val text: String) : TerminalLine()
    data class UserCommand(val command: String) : TerminalLine()
    data class RemoteOutput(val text: String) : TerminalLine()
    data class ErrorMessage(val text: String) : TerminalLine()
}
