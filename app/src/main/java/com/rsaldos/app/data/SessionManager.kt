package com.rsaldos.app.data

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var currentUserId: Int
        get() = prefs.getInt(KEY_USER, -1)
        set(value) = prefs.edit().putInt(KEY_USER, value).apply()

    var biometricUserId: Int
        get() = prefs.getInt(KEY_BIO_USER, -1)
        set(value) = prefs.edit().putInt(KEY_BIO_USER, value).apply()

    var lastEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    fun currentUser(): User? = Repository.findUser(currentUserId)

    fun logout() {
        prefs.edit().remove(KEY_USER).apply()
    }

    companion object {
        private const val PREFS = "rsaldos_session"
        private const val KEY_USER = "user_id"
        private const val KEY_BIO_USER = "bio_user_id"
        private const val KEY_EMAIL = "last_email"
    }
}
