package com.girlspace.app.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object InnerCircleBiometric {

    fun isAvailable(activity: FragmentActivity): Boolean {
        val manager = BiometricManager.from(activity)
        val res = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return res == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String = "Unlock Inner Circle",
        subtitle: String = "Use biometrics to unlock",
        onSuccess: () -> Unit,
        onFailureMessage: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Use PIN")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled or device not ready, etc.
                    onFailureMessage(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailureMessage("Biometric not recognized")
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
