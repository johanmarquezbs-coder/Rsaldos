package com.rsaldos.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.AuthService
import com.rsaldos.app.data.CredentialStore
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.ActivityLoginBinding
import com.rsaldos.app.ui.common.BiometricHelper
import com.rsaldos.app.ui.common.Navigator
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.askText
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import kotlinx.coroutines.launch

/**
 * Inicio de sesión para los tres roles.
 *
 * - Correo y contraseña contra Firebase Authentication ([AuthService.signIn]).
 * - Huella dactilar: BiometricPrompt desbloquea las credenciales cifradas en [CredentialStore]
 *   y con ellas se inicia sesión en Firebase sin escribir la contraseña.
 * - El administrador confirma además un segundo factor (código de demostración).
 * - Los chips de demostración llenan las cuentas de prueba; si no existen se crean solas.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var credentials: CredentialStore
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)
        credentials = CredentialStore(this)

        binding.etEmail.setText(session.lastEmail)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnForgot.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java)
                .putExtra(RecoverPasswordActivity.EXTRA_EMAIL, binding.etEmail.text?.toString()?.trim()))
        }
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnBiometric.setOnClickListener { loginWithBiometric() }
        binding.btnOauth.visible(false)

        binding.chipDemoBuyer.setOnClickListener { fillDemo("comprador@rsaldos.co", "Comprador123") }
        binding.chipDemoSeller.setOnClickListener { fillDemo("vendedor@rsaldos.co", "Vendedor123") }
        binding.chipDemoAdmin.setOnClickListener { fillDemo("admin@rsaldos.co", "Admin123") }
    }

    /** Llena el formulario con una cuenta de prueba. */
    private fun fillDemo(email: String, password: String) {
        binding.etEmail.setText(email)
        binding.etPassword.setText(password)
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    /** Valida el formulario e inicia sesión con correo y contraseña. */
    private fun attemptLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (!Validators.isEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_required)
            return
        }
        signIn(email, password, viaBiometric = false)
    }

    /**
     * Llama a Firebase Authentication. Si es correcto continúa con el segundo factor (solo
     * administrador) o entra directamente; si falla, muestra el motivo.
     */
    private fun signIn(email: String, password: String, viaBiometric: Boolean) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val user = AuthService.signIn(email, password)
                failedAttempts = 0
                if (user.role == Role.ADMIN) askMfa(user, password, viaBiometric)
                else offerBiometric(user, password, viaBiometric)
            } catch (e: Exception) {
                failedAttempts++
                val message = getString(e.userMessage())
                if (viaBiometric) toast(message) else binding.tilPassword.error = message
            } finally {
                setLoading(false)
            }
        }
    }

    /** Bloquea los botones mientras se espera la respuesta de Firebase. */
    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnBiometric.isEnabled = !loading
        binding.btnLogin.text = getString(if (loading) R.string.login_loading else R.string.login_button)
    }

    /** Segundo factor del administrador. Si se cancela, se cierra la sesión de Firebase. */
    private fun askMfa(user: User, password: String, viaBiometric: Boolean) {
        askText(
            title = getString(R.string.mfa_title),
            message = getString(R.string.mfa_message, Repository.DEMO_MFA_CODE),
            hint = getString(R.string.field_code),
            positive = getString(R.string.mfa_verify),
            numeric = true
        ) { code ->
            if (code == Repository.DEMO_MFA_CODE) offerBiometric(user, password, viaBiometric)
            else {
                AuthService.signOut()
                toast(getString(R.string.mfa_invalid))
            }
        }
    }

    /**
     * Después de un ingreso con contraseña, si el dispositivo tiene huella y el usuario aún no
     * la activó, ofrece activarla. Confirmar la huella guarda las credenciales cifradas.
     */
    private fun offerBiometric(user: User, password: String, viaBiometric: Boolean) {
        if (viaBiometric || user.biometricEnabled || !BiometricHelper.isAvailable(this)) {
            completeLogin(user, password, viaBiometric)
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_fingerprint)
            .setTitle(R.string.bio_offer_title)
            .setMessage(R.string.bio_offer_message)
            .setCancelable(false)
            .setNegativeButton(R.string.bio_offer_later) { _, _ -> completeLogin(user, password, false) }
            .setPositiveButton(R.string.bio_offer_enable) { _, _ ->
                BiometricHelper.authenticate(this, getString(R.string.bio_enable_title), getString(R.string.bio_enable_subtitle)) {
                    user.biometricEnabled = true
                    Repository.updateProfile(user, "Activó el ingreso con huella")
                    toast(getString(R.string.bio_enabled))
                    completeLogin(user, password, false)
                }
            }
            .show()
    }

    /** Ingreso con huella: requiere haber activado la huella antes desde el perfil o el login. */
    private fun loginWithBiometric() {
        if (!credentials.hasCredentials()) {
            toast(getString(R.string.bio_not_enrolled))
            return
        }
        BiometricHelper.authenticate(
            this,
            getString(R.string.bio_prompt_title),
            getString(R.string.bio_prompt_subtitle, credentials.name.substringBefore(" ").ifBlank { credentials.email })
        ) {
            signIn(credentials.email, credentials.password, viaBiometric = true)
        }
    }

    /**
     * Abre la sesión: inicia la sincronización con Firestore, actualiza las credenciales de la
     * huella si el usuario la tiene activada y navega a la pantalla del rol.
     */
    private fun completeLogin(user: User, password: String, viaBiometric: Boolean) {
        session.lastEmail = user.email
        if (user.biometricEnabled) credentials.save(user.id, user.name, user.email, password)
        else if (credentials.uid == user.id) credentials.clear()
        Repository.start(user)
        Repository.log(user.name, if (viaBiometric) "Inicio de sesión con huella" else "Inicio de sesión",
            "Rol ${user.role.label}", AuditType.SECURITY)
        toast(getString(R.string.login_welcome, user.name.substringBefore(" ")))
        Navigator.openHome(this, user)
        finish()
    }
}
