package com.rsaldos.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.FragmentDashboardBinding
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addRow
import com.rsaldos.app.ui.common.badge
import com.rsaldos.app.ui.common.setBars

class AdminDashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as RoleHomeActivity
        binding.tvOverline.text = getString(R.string.role_admin)
        binding.tvGreeting.text = getString(R.string.admin_dashboard_title)
        binding.tvBadge.badge(getString(R.string.admin_mfa_active), R.color.rs_green, R.color.rs_green_soft)
        binding.tvBarsTitle.text = getString(R.string.dashboard_sales_by_category)
        binding.tvAlertsTitle.text = getString(R.string.admin_alerts)
        binding.btnAction1.text = getString(R.string.admin_new_user)
        binding.btnAction2.text = getString(R.string.admin_review_products)
        binding.btnAction1.setOnClickListener { startActivity(Intent(requireContext(), UserFormActivity::class.java)) }
        binding.btnAction2.setOnClickListener { host.navigateTo(R.id.nav_admin_products) }
        binding.chipPeriod.setOnCheckedStateChangeListener { _, _ -> refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun periodDays(): Int = when (binding.chipPeriod.checkedChipId) {
        R.id.chipToday -> 0
        R.id.chipWeek -> 7
        else -> 30
    }

    private fun refresh() {
        val host = requireActivity() as RoleHomeActivity
        val orders = Repository.allOrders().filter { Repository.inPeriod(it, periodDays()) }
        val sales = orders.filter { Repository.isSale(it) }
        val users = Repository.allUsers()
        val pendingPayments = Repository.allOrders().count { it.status == OrderStatus.PENDING_PAYMENT || it.status == OrderStatus.PAID }

        binding.kpi1.tvKpiLabel.text = getString(R.string.kpi_sales)
        binding.kpi1.tvKpiValue.text = Format.compactMoney(Repository.salesTotal(sales))
        binding.kpi1.tvKpiHint.text = getString(R.string.kpi_average_ticket,
            Format.compactMoney(if (sales.isEmpty()) 0L else Repository.salesTotal(sales) / sales.size))
        binding.kpi2.tvKpiLabel.text = getString(R.string.kpi_orders)
        binding.kpi2.tvKpiValue.text = orders.size.toString()
        binding.kpi2.tvKpiHint.text = getString(R.string.kpi_pending, orders.count { it.status == OrderStatus.PAID || it.status == OrderStatus.PREPARING })
        binding.kpi3.tvKpiLabel.text = getString(R.string.kpi_users)
        binding.kpi3.tvKpiValue.text = users.size.toString()
        binding.kpi3.tvKpiHint.text = getString(R.string.kpi_sellers, users.count { it.role == Role.SELLER })
        binding.kpi4.tvKpiLabel.text = getString(R.string.kpi_to_reconcile)
        binding.kpi4.tvKpiValue.text = pendingPayments.toString()
        binding.kpi4.tvKpiHint.text = getString(R.string.kpi_review)

        val total = Repository.salesByCategory().filter { it.value > 0 }
            .map { it.key.label to it.value }.sortedByDescending { it.second }
        binding.containerBars.setBars(total.ifEmpty { listOf(getString(R.string.no_sales) to 0L) })

        binding.containerAlerts.removeAllViews()
        Repository.auditLog().filter { it.type == AuditType.SECURITY }.take(2).forEach {
            binding.containerAlerts.addRow(R.drawable.ic_warning, R.color.rs_red, it.action, it.detail, it.date.substringAfter(" ")) {
                host.navigateTo(R.id.nav_admin_audit)
            }
        }
        val inReview = Repository.allProducts().count { it.status == ProductStatus.REVIEW }
        if (inReview > 0) {
            binding.containerAlerts.addRow(R.drawable.ic_inventory, R.color.rs_blue,
                getString(R.string.alert_products_review, inReview), getString(R.string.alert_products_review_sub)) {
                host.navigateTo(R.id.nav_admin_products)
            }
        }
        val pendingSellers = users.count { it.status == UserStatus.PENDING }
        if (pendingSellers > 0) {
            binding.containerAlerts.addRow(R.drawable.ic_people, R.color.rs_orange,
                getString(R.string.alert_sellers_pending, pendingSellers), getString(R.string.alert_sellers_pending_sub)) {
                host.navigateTo(R.id.nav_admin_users)
            }
        }
        val blocked = users.count { it.status == UserStatus.BLOCKED }
        if (blocked > 0) {
            binding.containerAlerts.addRow(R.drawable.ic_lock, R.color.rs_graphite,
                getString(R.string.alert_blocked, blocked), getString(R.string.alert_blocked_sub)) {
                host.navigateTo(R.id.nav_admin_users)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
