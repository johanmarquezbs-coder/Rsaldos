package com.rsaldos.app.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.ActivityLoginBinding
import com.rsaldos.app.ui.common.BiometricHelper
import com.rsaldos.app.ui.common.Navigator
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.askText
import com.rsaldos.app.ui.common.toast

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private var failedAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.etEmail.setText(session.lastEmail)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnForgot.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java))
        }
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnBiometric.setOnClickListener { loginWithBiometric() }
        binding.btnOauth.setOnClickListener {
            toast(getString(R.string.login_oauth_pending))
        }

        binding.chipDemoBuyer.setOnClickListener { fillDemo("comprador@rsaldos.co", "Comprador123") }
        binding.chipDemoSeller.setOnClickListener { fillDemo("vendedor@rsaldos.co", "Vendedor123") }
        binding.chipDemoAdmin.setOnClickListener { fillDemo("admin@rsaldos.co", "Admin123") }
    }

    private fun fillDemo(email: String, password: String) {
        binding.etEmail.setText(email)
        binding.etPassword.setText(password)
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

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

        val user = Repository.login(email, password)
        if (user == null) {
            failedAttempts++
            Repository.log(email, "Intento de acceso fallido", "Intento $failedAttempts", AuditType.SECURITY)
            binding.tilPassword.error = getString(R.string.error_credentials)
            return
        }
        when (user.status) {
            UserStatus.BLOCKED -> {
                binding.tilEmail.error = getString(R.string.error_blocked)
                return
            }
            UserStatus.PENDING -> {
                binding.tilEmail.error = getString(R.string.error_pending_seller)
                return
            }
            else -> Unit
        }

        if (user.role == Role.ADMIN) askMfa(user) else completeLogin(user)
    }

    private fun askMfa(user: User) {
        askText(
            title = getString(R.string.mfa_title),
            message = getString(R.string.mfa_message, Repository.DEMO_MFA_CODE),
            hint = getString(R.string.field_code),
            positive = getString(R.string.mfa_verify),
            numeric = true
        ) { code ->
            if (code == Repository.DEMO_MFA_CODE) completeLogin(user)
            else toast(getString(R.string.mfa_invalid))
        }
    }

    private fun loginWithBiometric() {
        val user = Repository.findUser(session.biometricUserId)
        if (user == null || !user.biometricEnabled) {
            toast(getString(R.string.bio_not_enrolled))
            return
        }
        BiometricHelper.authenticate(
            this,
            getString(R.string.bio_prompt_title),
            getString(R.string.bio_prompt_subtitle, user.name)
        ) {
            Repository.log(user.name, "Inicio de sesión con huella", "Biometría del dispositivo", AuditType.SECURITY)
            if (user.role == Role.ADMIN) askMfa(user) else completeLogin(user)
        }
    }

    private fun completeLogin(user: User) {
        session.currentUserId = user.id
        session.lastEmail = user.email
        if (user.biometricEnabled) session.biometricUserId = user.id
        Repository.log(user.name, "Inicio de sesión", "Rol ${user.role.label}", AuditType.SECURITY)
        toast(getString(R.string.login_welcome, user.name.substringBefore(" ")))
        Navigator.openHome(this, user)
        finish()
    }
}
