package com.rsaldos.app.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.databinding.ActivityRegisterBinding
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.visible

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.appBar.root.title = getString(R.string.register_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.toggleRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) binding.tvSellerNote.visible(checkedId == R.id.btnRoleSeller)
        }
        binding.btnCreate.setOnClickListener { submit() }
    }

    private fun submit() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val document = binding.etDocument.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirm.text?.toString().orEmpty()

        listOf(binding.tilName, binding.tilDocument, binding.tilPhone, binding.tilEmail, binding.tilPassword, binding.tilConfirm)
            .forEach { it.error = null }

        var valid = true
        if (name.length < 3) { binding.tilName.error = getString(R.string.error_name); valid = false }
        if (document.length < 5) { binding.tilDocument.error = getString(R.string.error_document); valid = false }
        if (!Validators.isPhone(phone)) { binding.tilPhone.error = getString(R.string.error_phone); valid = false }
        if (!Validators.isEmail(email)) {
            binding.tilEmail.error = getString(R.string.error_email); valid = false
        } else if (Repository.emailExists(email)) {
            binding.tilEmail.error = getString(R.string.error_email_exists); valid = false
        }
        if (!Validators.isStrongPassword(password)) { binding.tilPassword.error = getString(R.string.password_rules); valid = false }
        if (password != confirm) { binding.tilConfirm.error = getString(R.string.error_password_match); valid = false }
        if (!binding.cbData.isChecked) {
            binding.cbData.error = getString(R.string.error_consent)
            valid = false
        } else {
            binding.cbData.error = null
        }
        if (!valid) return

        val role = if (binding.toggleRole.checkedButtonId == R.id.btnRoleSeller) Role.SELLER else Role.BUYER
        Repository.register(name, email, password, document, phone, role, binding.cbMarketing.isChecked)

        val message = if (role == Role.SELLER) R.string.register_done_seller else R.string.register_done_buyer
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.register_done_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_go_login) { _, _ -> finish() }
            .show()
    }
}
