package com.rsaldos.app.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.FragmentProfileBinding

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
                BiometricHelper.authenticate(host, getString(R.string.bio_enable_title), getString(R.string.bio_enable_subtitle)) {
                    user.biometricEnabled = true
                    host.session.biometricUserId = user.id
                    _binding?.switchBiometric?.isChecked = true
                    host.toast(getString(R.string.bio_enabled))
                }
            } else if (!checked && user.biometricEnabled) {
                user.biometricEnabled = false
                if (host.session.biometricUserId == user.id) host.session.biometricUserId = -1
            }
        }
        binding.switchLocation.setOnCheckedChangeListener { _, checked ->
            user.locationConsent = checked
            Repository.log(user.name, "Preferencia de ubicación", if (checked) "Autorizada" else "Retirada", AuditType.USERS)
        }
        binding.switchMarketing.setOnCheckedChangeListener { _, checked ->
            user.marketingConsent = checked
            Repository.log(user.name, "Consentimiento de publicidad", if (checked) "Otorgado" else "Retirado", AuditType.USERS)
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnChangePassword.setOnClickListener { changePassword() }
        binding.btnLogout.setOnClickListener { host.confirmLogout() }
        binding.btnDeleteAccount.setOnClickListener { deleteAccount(host) }
        binding.btnDeleteAccount.visible(user.role != Role.ADMIN)
    }

    private fun bindHeader() {
        binding.tvAvatar.avatar(user.initials, avatarColor(user.id))
        binding.tvName.text = user.name
        binding.tvEmail.text = user.email
        binding.tvRole.badge(user.role.label, R.color.rs_orange_dark, R.color.rs_orange_soft)
        binding.tvStatus.badge(user.status.label, user.status.color, user.status.bg)
    }

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
        Repository.log(user.name, "Actualizó su perfil", "Datos de contacto", AuditType.USERS)
        bindHeader()
        requireContext().toast(getString(R.string.profile_saved))
    }

    private fun changePassword() {
        requireContext().askText(
            getString(R.string.profile_change_password),
            getString(R.string.password_rules),
            getString(R.string.field_new_password),
            getString(R.string.action_save)
        ) { value ->
            if (Validators.isStrongPassword(value)) {
                Repository.changePassword(user, value)
                requireContext().toast(getString(R.string.password_changed))
            } else {
                requireContext().toast(getString(R.string.password_rules))
            }
        }
    }

    private fun deleteAccount(host: RoleHomeActivity) {
        requireContext().confirm(
            getString(R.string.profile_delete_account),
            getString(R.string.profile_delete_message),
            getString(R.string.action_continue)
        ) {
            BiometricHelper.authenticate(host, getString(R.string.profile_delete_account), getString(R.string.bio_confirm_identity)) {
                Repository.deleteUser(user, user, "Solicitud del titular")
                user.status = UserStatus.DELETED
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
