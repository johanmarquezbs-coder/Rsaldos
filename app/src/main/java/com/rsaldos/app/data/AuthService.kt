package com.rsaldos.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.rsaldos.app.R
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Authentication (correo y contraseña) para los tres roles.
 *
 * Cubre registro, inicio y cierre de sesión, recuperación de contraseña por correo, cambio de
 * contraseña y la creación de cuentas por parte del administrador. Después de autenticar,
 * valida el estado del perfil en Firestore: solo las cuentas activas pueden entrar.
 */
object AuthService {
    private const val ADMIN_APP = "rsaldos-admin-tools"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** UID del usuario autenticado en Firebase, o null si no hay sesión. */
    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Inicia sesión con correo y contraseña y devuelve el perfil del usuario.
     * Si la cuenta es de demostración y aún no existe, la crea (ver [DemoSeeder]).
     *
     * @throws AppException si las credenciales son inválidas o la cuenta no está activa.
     */
    suspend fun signIn(email: String, password: String): User {
        val normalized = email.trim().lowercase()
        val uid = try {
            auth.signInWithEmailAndPassword(normalized, password).await().user?.uid
        } catch (e: Exception) {
            val wrongCredentials = e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException
            if (wrongCredentials && DemoSeeder.isDemo(normalized, password)) {
                DemoSeeder.createDemoAccount(normalized)
            } else throw e
        } ?: throw AppException(R.string.error_generic)

        val profile = Repository.fetchProfile(uid) ?: DemoSeeder.profileIfDemo(uid, normalized)?.also {
            Repository.saveNewProfile(it)
        }
        if (profile == null) {
            auth.signOut()
            throw AppException(R.string.error_no_profile)
        }
        val error = when (profile.status) {
            UserStatus.BLOCKED -> R.string.error_blocked
            UserStatus.PENDING -> R.string.error_pending_seller
            UserStatus.DELETED -> R.string.error_credentials
            UserStatus.ACTIVE -> null
        }
        if (error != null) {
            auth.signOut()
            throw AppException(error)
        }
        return profile
    }

    /**
     * Restaura la sesión guardada por Firebase al abrir la app.
     * Devuelve null si no hay sesión o si la cuenta ya no está activa.
     */
    suspend fun restoreSession(): User? {
        val uid = currentUid ?: return null
        val profile = try { Repository.fetchProfile(uid) } catch (e: Exception) { null }
        if (profile == null || profile.status != UserStatus.ACTIVE) {
            auth.signOut()
            return null
        }
        return profile
    }

    /**
     * Registra una cuenta nueva (comprador o vendedor). Los vendedores quedan en estado
     * PENDING hasta que el administrador los apruebe. Al terminar se cierra la sesión para
     * que el usuario entre desde la pantalla de inicio.
     */
    suspend fun register(
        name: String, email: String, password: String, document: String, phone: String,
        role: Role, marketing: Boolean
    ): User {
        val result = auth.createUserWithEmailAndPassword(email.trim().lowercase(), password).await()
        val uid = result.user?.uid ?: throw AppException(R.string.error_generic)
        val status = if (role == Role.SELLER) UserStatus.PENDING else UserStatus.ACTIVE
        val user = User(
            id = uid, name = name, email = email.trim().lowercase(), document = document, phone = phone,
            role = role, status = status, marketingConsent = marketing,
            storeName = if (role == Role.SELLER) name else "", createdAt = Repository.today()
        )
        Repository.saveNewProfile(user)
        Repository.log(name, "Registro de cuenta", "Rol ${role.label} · estado ${status.label}", AuditType.USERS)
        auth.signOut()
        return user
    }

    /** Envía el correo de restablecimiento de contraseña de Firebase. */
    suspend fun sendPasswordReset(email: String) {
        auth.setLanguageCode("es")
        auth.sendPasswordResetEmail(email.trim().lowercase()).await()
    }

    /**
     * Cambia la contraseña del usuario autenticado. Firebase exige una autenticación reciente,
     * por eso primero se reautentica con la contraseña actual.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: throw AppException(R.string.error_generic)
        val credential = EmailAuthProvider.getCredential(user.email.orEmpty(), currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    /** Verifica la contraseña actual (se usa antes de activar el ingreso con huella). */
    suspend fun verifyPassword(password: String) {
        val user = auth.currentUser ?: throw AppException(R.string.error_generic)
        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
    }

    /** Cierra la sesión de Firebase. */
    fun signOut() = auth.signOut()

    /**
     * Crea una cuenta en Firebase Authentication sin afectar la sesión del administrador,
     * usando una instancia secundaria de FirebaseApp con la misma configuración.
     */
    suspend fun createAccountAsAdmin(email: String, password: String): String {
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp())
        try {
            val result = secondaryAuth.createUserWithEmailAndPassword(email.trim().lowercase(), password).await()
            return result.user?.uid ?: throw AppException(R.string.error_generic)
        } finally {
            secondaryAuth.signOut()
        }
    }

    /** Devuelve (o crea) la instancia secundaria de FirebaseApp para operaciones del administrador. */
    private fun secondaryApp(): FirebaseApp {
        FirebaseApp.getApps(appContext()).firstOrNull { it.name == ADMIN_APP }?.let { return it }
        val options: FirebaseOptions = FirebaseApp.getInstance().options
        return FirebaseApp.initializeApp(appContext(), options, ADMIN_APP)
    }

    /** Contexto de la aplicación a partir de la instancia principal de Firebase. */
    private fun appContext(): Context = FirebaseApp.getInstance().applicationContext
}
