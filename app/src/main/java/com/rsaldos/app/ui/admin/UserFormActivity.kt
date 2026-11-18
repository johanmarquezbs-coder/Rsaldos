package com.rsaldos.app.ui.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.data.AuthService
import com.rsaldos.app.data.userMessage
import kotlinx.coroutines.launch
import com.rsaldos.app.R
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.ActivityUserFormBinding
import com.rsaldos.app.ui.common.Validators
import com.rsaldos.app.ui.common.avatar
import com.rsaldos.app.ui.common.avatarColor
import com.rsaldos.app.ui.common.badge
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible

/**
 * Formulario de usuarios del Administrador (Create y Update del CRUD de usuarios).
 *
 * Crear: registra la cuenta en Firebase Authentication con una contraseña temporal (sin
 * cerrar la sesión del administrador) y guarda el perfil en Firestore. Editar: actualiza
 * datos, rol y estado con un motivo obligatorio que queda en la auditoría. Eliminar: baja
 * lógica. También permite enviar al usuario el correo para restablecer su contraseña.
 */
class UserFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserFormBinding
    private lateinit var admin: User
    private var editing: User? = null
    private var sessions = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = SessionManager(this).currentUser()
        if (current == null) { finish(); return }
        admin = current
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        editing = if (id.isNotEmpty()) Repository.findUser(id) else null

        binding.appBar.root.title = getString(if (editing == null) R.string.user_new_title else R.string.user_edit_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnCloseSessions.setOnClickListener {
            sessions = 0
            updateSessions()
            toast(getString(R.string.user_sessions_closed))
        }
        binding.btnSendReset.setOnClickListener { sendReset() }
        fill()
    }

    /** Llena el formulario con el usuario a editar o con valores por defecto si es nuevo. */
    private fun fill() {
        val u = editing
        val isEdit = u != null
        binding.layoutHeader.visible(isEdit)
        binding.tilPassword.visible(!isEdit)
        binding.tilReason.visible(isEdit)
        binding.tvAuditNote.visible(isEdit)
        binding.layoutSessions.visible(isEdit)
        binding.btnDelete.visible(isEdit && u?.id != admin.id)
        binding.btnSendReset.visible(isEdit)

        if (u == null) {
            binding.toggleRole.check(R.id.btnRoleBuyer)
            binding.switchActive.isChecked = true
            binding.switchForcePassword.isChecked = true
            binding.btnSave.text = getString(R.string.user_create)
            return
        }
        binding.tvAvatar.avatar(u.initials, avatarColor(u.id))
        binding.tvHeaderName.text = u.name
        binding.tvHeaderMeta.text = getString(R.string.user_header_meta, u.email, u.createdAt)
        binding.tvHeaderStatus.badge(u.status.label, u.status.color, u.status.bg)
        binding.etName.setText(u.name)
        binding.etEmail.setText(u.email)
        binding.etDocument.setText(u.document)
        binding.etPhone.setText(u.phone)
        binding.toggleRole.check(
            when (u.role) {
                Role.BUYER -> R.id.btnRoleBuyer
                Role.SELLER -> R.id.btnRoleSeller
                Role.ADMIN -> R.id.btnRoleAdmin
            }
        )
        binding.switchActive.isChecked = u.status == UserStatus.ACTIVE
        binding.switchForcePassword.isChecked = u.mustChangePassword
        sessions = u.openSessions
        updateSessions()
        if (u.id == admin.id) {
            binding.btnRoleBuyer.isEnabled = false
            binding.btnRoleSeller.isEnabled = false
            binding.switchActive.isEnabled = false
        }
    }

    /** Muestra cuántas sesiones abiertas tiene el usuario. */
    private fun updateSessions() {
        binding.tvSessions.text = resources.getQuantityString(R.plurals.sessions_open, sessions, sessions)
        binding.btnCloseSessions.isEnabled = sessions > 0
    }

    /** Rol elegido en el selector. */
    private fun selectedRole(): Role = when (binding.toggleRole.checkedButtonId) {
        R.id.btnRoleSeller -> Role.SELLER
        R.id.btnRoleAdmin -> Role.ADMIN
        else -> Role.BUYER
    }

    /** Valida el formulario y crea o actualiza el usuario en Firebase. */
    private fun save() {
        listOf(binding.tilName, binding.tilEmail, binding.tilDocument, binding.tilPhone, binding.tilPassword, binding.tilReason)
            .forEach { it.error = null }
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val document = binding.etDocument.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val reason = binding.etReason.text?.toString()?.trim().orEmpty()
        val u = editing

        var ok = true
        if (name.length < 3) { binding.tilName.error = getString(R.string.error_name); ok = false }
        if (!Validators.isEmail(email)) { binding.tilEmail.error = getString(R.string.error_email); ok = false }
        else if (Repository.emailExists(email, u?.id ?: "")) { binding.tilEmail.error = getString(R.string.error_email_exists); ok = false }
        if (document.length < 5) { binding.tilDocument.error = getString(R.string.error_document); ok = false }
        if (!Validators.isPhone(phone)) { binding.tilPhone.error = getString(R.string.error_phone); ok = false }
        if (u == null && !Validators.isStrongPassword(password)) { binding.tilPassword.error = getString(R.string.password_rules); ok = false }
        if (u != null && reason.length < 10) { binding.tilReason.error = getString(R.string.error_reason); ok = false }
        if (!ok) return

        val status = if (binding.switchActive.isChecked) UserStatus.ACTIVE else UserStatus.BLOCKED
        if (u == null) {
            binding.btnSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    val created = Repository.createUser(
                        admin,
                        User(
                            id = "", name = name, email = email, document = document, phone = phone,
                            role = selectedRole(), status = status, mustChangePassword = binding.switchForcePassword.isChecked,
                            storeName = if (selectedRole() == Role.SELLER) name else "", openSessions = 0
                        ),
                        password
                    )
                    toast(getString(R.string.user_created, created.email))
                    finish()
                } catch (e: Exception) {
                    val message = e.userMessage()
                    if (message == R.string.error_email_exists) binding.tilEmail.error = getString(message)
                    else toast(getString(message))
                } finally {
                    binding.btnSave.isEnabled = true
                }
            }
            return
        } else {
            val updated = u.copy(
                name = name, email = email, document = document, phone = phone, role = selectedRole(),
                status = if (u.status == UserStatus.PENDING && binding.switchActive.isChecked) UserStatus.ACTIVE else status,
                mustChangePassword = binding.switchForcePassword.isChecked,
                openSessions = sessions
            )
            Repository.updateUser(admin, updated, reason)
            toast(getString(R.string.users_updated))
        }
        finish()
    }

    /** Envía al usuario el correo de Firebase para restablecer su contraseña. */
    private fun sendReset() {
        val u = editing ?: return
        lifecycleScope.launch {
            try {
                AuthService.sendPasswordReset(u.email)
                Repository.log(admin.name, "Envió restablecimiento de contraseña", u.email, com.rsaldos.app.data.AuditType.SECURITY)
                toast(getString(R.string.user_reset_sent, u.email))
            } catch (e: Exception) {
                toast(getString(e.userMessage()))
            }
        }
    }

    /** Eliminación lógica del usuario con motivo obligatorio. */
    private fun delete() {
        val u = editing ?: return
        val reason = binding.etReason.text?.toString()?.trim().orEmpty()
        if (reason.length < 10) {
            binding.tilReason.error = getString(R.string.error_reason)
            return
        }
        confirm(getString(R.string.users_delete_title), getString(R.string.users_delete_message, u.name), getString(R.string.action_delete)) {
            Repository.deleteUser(admin, u, reason)
            toast(getString(R.string.users_deleted))
            finish()
        }
    }

    companion object {
        const val EXTRA_ID = "user_id"
    }
}
