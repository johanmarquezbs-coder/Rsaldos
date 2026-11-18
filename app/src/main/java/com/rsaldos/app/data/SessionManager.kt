package com.rsaldos.app.data

import android.content.Context

/**
 * Sesión local del usuario.
 *
 * La sesión real la mantiene Firebase Authentication; esta clase solo recuerda el último correo
 * usado y da acceso al perfil cargado en [Repository]. Al cerrar sesión detiene los listeners
 * de Firestore y desconecta Firebase.
 */
class SessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Último correo con el que se inició sesión (se precarga en el login). */
    var lastEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** Perfil del usuario autenticado o null si no hay sesión. */
    fun currentUser(): User? = Repository.me

    /** Cierra la sesión: detiene la sincronización y desconecta Firebase Authentication. */
    fun logout() {
        Repository.stop()
        AuthService.signOut()
    }

    companion object {
        private const val PREFS = "rsaldos_session"
        private const val KEY_EMAIL = "last_email"
    }
}
