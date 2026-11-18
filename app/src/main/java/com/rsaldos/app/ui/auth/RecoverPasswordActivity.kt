package com.rsaldos.app.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.R
import com.rsaldos.app.data.AuthService
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.ActivityRecoverBinding
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import kotlinx.coroutines.launch

/**
 * Recuperación de contraseña con Firebase Authentication.
 *
 * Paso 1: el usuario escribe su correo y Firebase envía un enlace de restablecimiento.
 * Paso 2: se confirma el envío; el usuario crea la nueva contraseña desde el enlace del correo
 * y vuelve a iniciar sesión. Puede reenviar el correo si no le llegó.
 */
class RecoverPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecoverBinding
    private var email = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appBar.root.title = getString(R.string.recover_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }
        binding.tvStepMessage.text = getString(R.string.recover_step1_message)
        intent.getStringExtra(EXTRA_EMAIL)?.let { binding.etEmail.setText(it) }

        listOf(binding.tvDemoCode, binding.tilCode, binding.tilNewPassword, binding.tilConfirm).forEach { it.visible(false) }
        binding.btnReset.text = getString(R.string.action_go_login)

        binding.btnSend.setOnClickListener { sendLink(firstTime = true) }
        binding.btnResend.setOnClickListener { sendLink(firstTime = false) }
        binding.btnReset.setOnClickListener { finish() }
    }

    /** Envía (o reenvía) el correo de restablecimiento de Firebase. */
    private fun sendLink(firstTime: Boolean) {
        if (firstTime) {
            email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (!Validators.isEmail(email)) {
                binding.tilEmail.error = getString(R.string.error_email)
                return
            }
            binding.tilEmail.error = null
        }
        binding.btnSend.isEnabled = false
        binding.btnResend.isEnabled = false
        lifecycleScope.launch {
            try {
                AuthService.sendPasswordReset(email)
                if (firstTime) showStep2() else toast(getString(R.string.recover_resent))
            } catch (e: Exception) {
                // Por seguridad no se revela si el correo existe: se muestra el mismo mensaje.
                if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    if (firstTime) showStep2() else toast(getString(R.string.recover_resent))
                } else toast(getString(e.userMessage()))
            } finally {
                binding.btnSend.isEnabled = true
                binding.btnResend.isEnabled = true
            }
        }
    }

    /** Muestra la confirmación del envío. */
    private fun showStep2() {
        binding.layoutStep1.visible(false)
        binding.layoutStep2.visible(true)
        binding.tvStepTitle.text = getString(R.string.recover_step2_title)
        binding.tvStepMessage.text = getString(R.string.recover_step2_message, email)
    }

    companion object {
        const val EXTRA_EMAIL = "email"
    }
}
