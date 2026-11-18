package com.rsaldos.app.data

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rsaldos.app.R

/**
 * Error de negocio con un mensaje traducible (recurso de strings.xml) para mostrar al usuario.
 */
class AppException(@StringRes val messageRes: Int, cause: Throwable? = null) : Exception(cause)

/**
 * Traduce cualquier excepción de Firebase o de red al recurso de texto que se muestra en pantalla.
 */
@StringRes
fun Throwable.userMessage(): Int = when (this) {
    is AppException -> messageRes
    is FirebaseAuthUserCollisionException -> R.string.error_email_exists
    is FirebaseAuthWeakPasswordException -> R.string.password_rules
    is FirebaseAuthInvalidUserException -> R.string.error_credentials
    is FirebaseAuthInvalidCredentialsException -> R.string.error_credentials
    is FirebaseAuthRecentLoginRequiredException -> R.string.error_recent_login
    is FirebaseTooManyRequestsException -> R.string.error_too_many
    is FirebaseNetworkException -> R.string.error_network
    is FirebaseFirestoreException -> if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        R.string.error_permission
    } else R.string.error_generic
    is java.io.IOException -> R.string.error_network
    else -> R.string.error_generic
}
