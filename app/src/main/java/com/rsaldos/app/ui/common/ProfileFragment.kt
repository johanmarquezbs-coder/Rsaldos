package com.rsaldos.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.AuthService
import com.rsaldos.app.data.CredentialStore
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

/**
 * Perfil del usuario (comprador, vendedor o administrador).
 *
 * Permite editar los datos de contacto (Update del CRUD propio en Firestore), activar o
 * desactivar el ingreso con huella, gestionar los consentimientos, cambiar la contraseña en
 * Firebase Authentication, cerrar sesión y solicitar la eliminación de la cuenta.
 */
class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var user: User

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as RoleHomeActivity
        user = host.user
        bindHeader()

        binding.etName.setText(user.name)
        binding.etDocument.setText(user.document)
        binding.etPhone.setText(user.phone)
        binding.etAddress.setText(user.address)
        binding.layoutPayments.visible(user.role == Role.BUYER)
        bindSeller()

        binding.switchBiometric.isChecked = user.biometricEnabled
        binding.switchLocation.isChecked = user.locationConsent
        binding.switchMarketing.isChecked = user.marketingConsent

        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (checked && !user.biometricEnabled) {
                binding.switchBiometric.isChecked = false
                enableBiometric(host)
            } else if (!checked && user.biometricEnabled) {
                user.biometricEnabled = false
                CredentialStore(requireContext()).clear()
                Repository.updateProfile(user, "Desactivó el ingreso con huella")
            }
        }
        binding.switchLocation.setOnCheckedChangeListener { _, checked ->
            if (checked == user.locationConsent) return@setOnCheckedChangeListener
            user.locationConsent = checked
            Repository.updateProfile(user, "Preferencia de ubicación: " + if (checked) "autorizada" else "retirada")
        }
        binding.switchMarketing.setOnCheckedChangeListener { _, checked ->
            if (checked == user.marketingConsent) return@setOnCheckedChangeListener
            user.marketingConsent = checked
            Repository.updateProfile(user, "Consentimiento de publicidad: " + if (checked) "otorgado" else "retirado")
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnChangePassword.setOnClickListener { changePassword() }
        binding.btnLogout.setOnClickListener { host.confirmLogout() }
        binding.btnDeleteAccount.setOnClickListener { deleteAccount(host) }
        binding.btnDeleteAccount.visible(user.role != Role.ADMIN)
    }

    /**
     * Activa la huella: pide la contraseña actual (se verifica con Firebase), confirma la
     * huella con BiometricPrompt y guarda las credenciales cifradas en el dispositivo.
     */
    private fun enableBiometric(host: RoleHomeActivity) {
        if (!BiometricHelper.isAvailable(host)) {
            BiometricHelper.authenticate(host, "", "") { }
            return
        }
        host.askText(getString(R.string.bio_enable_title), getString(R.string.bio_password_message),
            getString(R.string.field_password), getString(R.string.action_continue), password = true) { password ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    AuthService.verifyPassword(password)
                    BiometricHelper.authenticate(host, getString(R.string.bio_enable_title), getString(R.string.bio_enable_subtitle)) {
                        CredentialStore(host).save(user.id, user.name, user.email, password)
                        user.biometricEnabled = true
                        Repository.updateProfile(user, "Activó el ingreso con huella")
                        _binding?.switchBiometric?.isChecked = true
                        host.toast(getString(R.string.bio_enabled))
                    }
                } catch (e: Exception) {
                    host.toast(getString(e.userMessage()))
                }
            }
        }
    }

    /** Pinta el avatar, nombre, correo, rol y estado. */
    private fun bindHeader() {
        binding.tvAvatar.avatar(user.initials, avatarColor(user.id))
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvRole.badge(user.role.label, R.color.rs_orange_dark, R.color.rs_orange_soft)
        binding.tvStatus.badge(user.status.label, user.status.color, user.status.bg)
    }

    /** Indicadores y datos de la tienda (solo vendedor). */
    private fun bindSeller() {
        val isSeller = user.role == Role.SELLER
        binding.cardSeller.visible(isSeller)
        if (!isSeller) return
        val products = Repository.productsOfSeller(user.id)
        val orders = Repository.ordersOfSeller(user.id).filter { it.status != OrderStatus.CANCELLED }
        val rating = if (products.isEmpty()) 0.0 else products.map { it.rating }.average()

        binding.kpiRating.tvKpiLabel.text = getString(R.string.kpi_rating)
        binding.kpiRating.tvKpiValue.text = String.format("%.1f ★", rating).replace('.', ',')
        binding.kpiRating.tvKpiHint.text = getString(R.string.kpi_verified)
        binding.kpiSales.tvKpiLabel.text = getString(R.string.kpi_orders)
        binding.kpiSales.tvKpiValue.text = orders.size.toString()
        binding.kpiSales.tvKpiHint.text = ""
        binding.kpiProducts.tvKpiLabel.text = getString(R.string.kpi_published)
        binding.kpiProducts.tvKpiValue.text = products.count { it.status == ProductStatus.PUBLISHED }.toString()
        binding.kpiProducts.tvKpiHint.text = ""
        binding.etStore.setText(user.storeName)
        binding.tvSellerInfo.text = getString(R.string.profile_seller_info, user.document, user.createdAt)
    }

    /** Valida y guarda los datos de contacto en Firestore. */
    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        binding.tilName.error = null
        binding.tilPhone.error = null
        if (name.length < 3) { binding.tilName.error = getString(R.string.error_name); return }
        if (!Validators.isPhone(phone)) { binding.tilPhone.error = getString(R.string.error_phone); return }
        user.name = name
        user.phone = phone
        user.address = binding.etAddress.text?.toString()?.trim().orEmpty()
        if (user.role == Role.SELLER) user.storeName = binding.etStore.text?.toString()?.trim().orEmpty()
        Repository.updateProfile(user, "Datos de contacto")
        bindHeader()
        requireContext().toast(getString(R.string.profile_saved))
    }

    /** Cambia la contraseña: pide la actual (reautenticación) y luego la nueva. */
    private fun changePassword() {
        val ctx = requireContext()
        ctx.askText(getString(R.string.profile_change_password), getString(R.string.password_current_message),
            getString(R.string.field_current_password), getString(R.string.action_continue), password = true) { current ->
            ctx.askText(getString(R.string.profile_change_password), getString(R.string.password_rules),
                getString(R.string.field_new_password), getString(R.string.action_save), password = true) { value ->
                if (!Validators.isStrongPassword(value)) {
                    ctx.toast(getString(R.string.password_rules))
                } else viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        AuthService.changePassword(current, value)
                        val store = CredentialStore(ctx)
                        if (store.uid == user.id) store.save(user.id, user.name, user.email, value)
                        Repository.log(user.name, "Cambio de contraseña", "Desde el perfil", AuditType.SECURITY)
                        ctx.toast(getString(R.string.password_changed))
                    } catch (e: Exception) {
                        ctx.toast(getString(e.userMessage()))
                    }
                }
            }
        }
    }

    /**
     * Eliminación de la cuenta a solicitud del titular: se confirma con la huella (o se informa
     * si no hay), se marca como eliminada en Firestore y se cierra la sesión.
     */
    private fun deleteAccount(host: RoleHomeActivity) {
        requireContext().confirm(
            getString(R.string.profile_delete_account),
            getString(R.string.profile_delete_message),
            getString(R.string.action_continue)
        ) {
            BiometricHelper.authenticate(host, getString(R.string.profile_delete_account), getString(R.string.bio_confirm_identity)) {
                Repository.deleteUser(user, user, "Solicitud del titular")
                CredentialStore(host).clear()
                host.session.logout()
                requireContext().toast(getString(R.string.profile_deleted))
                Navigator.openLogin(host)
                host.finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
