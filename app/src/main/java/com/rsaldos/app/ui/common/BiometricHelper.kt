package com.rsaldos.app.ui.common

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R

/**
 * Autenticación biométrica con BiometricPrompt (huella dactilar o rostro, clase débil o fuerte).
 *
 * Si el dispositivo no tiene sensor o no hay huellas registradas, informa al usuario y le
 * sugiere usar la contraseña; no hay modo simulado.
 */
object BiometricHelper {
    /** Indica si el dispositivo tiene biometría disponible y al menos una huella registrada. */
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Muestra el diálogo biométrico del sistema y ejecuta [onSuccess] si la huella es válida. */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit
    ) {
        if (!isAvailable(activity)) {
            MaterialAlertDialogBuilder(activity)
                .setIcon(R.drawable.ic_fingerprint)
                .setTitle(R.string.bio_unavailable_title)
                .setMessage(R.string.bio_unavailable_message)
                .setPositiveButton(R.string.bio_use_password, null)
                .show()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        activity.toast(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    activity.toast(activity.getString(R.string.bio_failed))
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.bio_use_password))
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
