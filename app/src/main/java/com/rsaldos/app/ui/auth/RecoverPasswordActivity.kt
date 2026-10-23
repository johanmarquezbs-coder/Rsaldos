package com.rsaldos.app.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.ActivityRecoverBinding
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class RecoverPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecoverBinding
    private var email = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appBar.root.title = getString(R.string.recover_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.btnSend.setOnClickListener { sendCode() }
        binding.btnResend.setOnClickListener { toast(getString(R.string.recover_resent)) }
        binding.btnReset.setOnClickListener { resetPassword() }
    }

    private fun sendCode() {
        email = binding.etEmail.text?.toString()?.trim().orEmpty()
        if (!Validators.isEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_email)
            return
        }
        binding.tilEmail.error = null
        binding.layoutStep1.visible(false)
        binding.layoutStep2.visible(true)
        binding.tvStepTitle.text = getString(R.string.recover_step2_title)
        binding.tvStepMessage.text = getString(R.string.recover_step2_message, email)
        binding.tvDemoCode.text = getString(R.string.recover_demo_code, Repository.DEMO_RECOVERY_CODE)
    }

    private fun resetPassword() {
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
        val password = binding.etNewPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()
        binding.tilCode.error = null
        binding.tilNewPassword.error = null
        binding.tilConfirm.error = null

        if (code != Repository.DEMO_RECOVERY_CODE) { binding.tilCode.error = getString(R.string.error_code); return }
        if (!Validators.isStrongPassword(password)) { binding.tilNewPassword.error = getString(R.string.password_rules); return }
        if (password != confirm) { binding.tilConfirm.error = getString(R.string.error_password_match); return }

        Repository.findUserByEmail(email)?.let { Repository.changePassword(it, password) }
        toast(getString(R.string.recover_done))
        finish()
    }
}
