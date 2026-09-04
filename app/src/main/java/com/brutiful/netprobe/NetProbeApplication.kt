package com.brutiful.netprobe

import android.app.Application
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class NetProbeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupSecurityProviders()
    }

    private fun setupSecurityProviders() {
        val tag = "NetProbeSecurity"
        Log.i(tag, "Initializing Security Providers...")

        // Log existing providers for diagnostics
        Security.getProviders().forEachIndexed { index, provider ->
            Log.d(tag, "Existing Provider[$index]: ${provider.name} v${provider.version} - ${provider.javaClass.name}")
        }

        val existingBc = Security.getProvider("BC")
        if (existingBc != null) {
            val className = existingBc.javaClass.name
            if (className != BouncyCastleProvider::class.java.name) {
                Log.w(tag, "Found internal/conflicting BC provider: $className. Removing it to prefer bundled version.")
                Security.removeProvider("BC")
            } else {
                Log.i(tag, "BouncyCastle provider already correctly registered.")
                return
            }
        }

        try {
            // Insert bundled Bouncy Castle provider at the highest priority (position 1)
            val result = Security.insertProviderAt(BouncyCastleProvider(), 1)
            if (result == -1) {
                Log.e(tag, "Failed to insert BouncyCastleProvider.")
            } else {
                Log.i(tag, "BouncyCastleProvider successfully registered at position $result.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during BouncyCastleProvider registration", e)
        }

        // Final verification
        val finalBc = Security.getProvider("BC")
        if (finalBc != null) {
            Log.i(tag, "Active BC Provider: ${finalBc.name} v${finalBc.version} - ${finalBc.javaClass.name}")
        } else {
            Log.e(tag, "BC Provider NOT found after setup!")
        }
    }
}
