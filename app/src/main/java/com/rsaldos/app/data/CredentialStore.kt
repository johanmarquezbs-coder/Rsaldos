package com.rsaldos.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Guarda cifradas (AES-256 con una llave del Android Keystore) las credenciales del último
 * usuario que activó el ingreso con huella.
 *
 * Solo se leen después de que BiometricPrompt confirma la huella del dueño del dispositivo;
 * con ellas se inicia sesión en Firebase Authentication sin escribir la contraseña.
 */
class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Indica si hay credenciales guardadas para el ingreso biométrico. */
    fun hasCredentials(): Boolean = !prefs.getString(KEY_EMAIL, null).isNullOrBlank()

    /** Correo asociado a la huella (para mostrarlo en el diálogo). */
    val email: String get() = prefs.getString(KEY_EMAIL, "").orEmpty()

    /** Nombre del usuario asociado a la huella. */
    val name: String get() = prefs.getString(KEY_NAME, "").orEmpty()

    /** UID del usuario asociado a la huella. */
    val uid: String get() = prefs.getString(KEY_UID, "").orEmpty()

    /** Contraseña cifrada; solo debe leerse tras una autenticación biométrica exitosa. */
    val password: String get() = prefs.getString(KEY_PASSWORD, "").orEmpty()

    /** Guarda las credenciales del usuario que activa la huella. */
    fun save(uid: String, name: String, email: String, password: String) {
        prefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** Borra las credenciales (al desactivar la huella o eliminar la cuenta). */
    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val FILE = "rsaldos_biometric"
        private const val KEY_UID = "uid"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
    }
}
