package com.rsaldos.app.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.FragmentDashboardBinding
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addRow
import com.rsaldos.app.ui.common.badge
import com.rsaldos.app.ui.common.setBars

class SellerDashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as RoleHomeActivity
        val user = host.user
        binding.tvOverline.text = getString(R.string.seller_overline)
        binding.tvGreeting.text = user.storeName.ifBlank { user.name }
        if (user.status == UserStatus.ACTIVE) binding.tvBadge.badge(getString(R.string.seller_verified), R.color.rs_green, R.color.rs_green_soft)
        else binding.tvBadge.badge(user.status.label, user.status.color, user.status.bg)

        binding.tvBarsTitle.text = getString(R.string.dashboard_sales_by_category)
        binding.tvAlertsTitle.text = getString(R.string.seller_inventory_alerts)
        binding.btnAction1.text = getString(R.string.nav_new_product)
        binding.btnAction2.text = getString(R.string.seller_to_dispatch)
        binding.btnAction1.setOnClickListener { startActivity(Intent(requireContext(), ProductFormActivity::class.java)) }
        binding.btnAction2.setOnClickListener { host.navigateTo(R.id.nav_seller_orders) }
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
        val user = (requireActivity() as RoleHomeActivity).user
        val products = Repository.productsOfSeller(user.id)
        val allOrders = Repository.ordersOfSeller(user.id)
        val periodOrders = allOrders.filter { Repository.isSale(it) && Repository.inPeriod(it, periodDays()) }
        val sales = periodOrders.flatMap { it.lines }.filter { it.sellerId == user.id }.sumOf { it.subtotal }
        val toDispatch = allOrders.count { it.status == OrderStatus.PAID || it.status == OrderStatus.PREPARING }

        binding.kpi1.tvKpiLabel.text = getString(R.string.kpi_sales)
        binding.kpi1.tvKpiValue.text = Format.compactMoney(sales)
        binding.kpi1.tvKpiHint.text = resources.getQuantityString(R.plurals.orders_count, periodOrders.size, periodOrders.size)
        binding.kpi2.tvKpiLabel.text = getString(R.string.kpi_to_dispatch)
        binding.kpi2.tvKpiValue.text = toDispatch.toString()
        binding.kpi2.tvKpiHint.text = getString(R.string.kpi_today_cutoff)
        binding.kpi3.tvKpiLabel.text = getString(R.string.kpi_published)
        binding.kpi3.tvKpiValue.text = products.count { it.status == ProductStatus.PUBLISHED }.toString()
        binding.kpi3.tvKpiHint.text = getString(R.string.kpi_units, products.sumOf { it.stock })
        binding.kpi4.tvKpiLabel.text = getString(R.string.kpi_in_review)
        binding.kpi4.tvKpiValue.text = products.count { it.status == ProductStatus.REVIEW }.toString()
        binding.kpi4.tvKpiHint.text = getString(R.string.kpi_admin_review)

        val byCategory = periodOrders.flatMap { it.lines }.filter { it.sellerId == user.id }
            .groupBy { it.category.label }
            .map { (label, lines) -> label to lines.sumOf { it.subtotal } }
            .sortedByDescending { it.second }
        binding.containerBars.setBars(byCategory.ifEmpty { listOf(getString(R.string.no_sales) to 0L) })

        binding.containerAlerts.removeAllViews()
        val host = requireActivity() as RoleHomeActivity
        products.filter { it.stock <= 1 && it.status == ProductStatus.PUBLISHED }.forEach {
            binding.containerAlerts.addRow(R.drawable.ic_warning, R.color.rs_yellow, it.title,
                getString(R.string.alert_low_stock, it.stock)) { openForm(it.id) }
        }
        products.filter { it.status == ProductStatus.REJECTED }.forEach {
            binding.containerAlerts.addRow(R.drawable.ic_cancel, R.color.rs_red, it.title,
                getString(R.string.alert_rejected, it.rejectReason)) { openForm(it.id) }
        }
        if (toDispatch > 0) {
            binding.containerAlerts.addRow(R.drawable.ic_truck, R.color.rs_orange,
                getString(R.string.alert_dispatch_title, toDispatch), getString(R.string.alert_dispatch_sub)) {
                host.navigateTo(R.id.nav_seller_orders)
            }
        }
        if (binding.containerAlerts.childCount == 0) {
            binding.containerAlerts.addRow(R.drawable.ic_check_circle, R.color.rs_green,
                getString(R.string.alert_none), getString(R.string.alert_none_sub))
        }
    }

    private fun openForm(productId: Int) {
        startActivity(Intent(requireContext(), ProductFormActivity::class.java)
            .putExtra(ProductFormActivity.EXTRA_ID, productId))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
