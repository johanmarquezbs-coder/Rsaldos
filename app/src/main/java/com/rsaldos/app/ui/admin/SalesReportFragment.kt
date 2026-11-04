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
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentDashboardBinding
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addRow
import com.rsaldos.app.ui.common.badge
import com.rsaldos.app.ui.common.setBars

class SalesReportFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as RoleHomeActivity
        binding.tvOverline.text = getString(R.string.report_overline)
        binding.tvGreeting.text = getString(R.string.nav_reports)
        binding.tvBadge.badge(getString(R.string.report_currency), R.color.rs_blue, R.color.rs_blue_soft)
        binding.tvBarsTitle.text = getString(R.string.dashboard_sales_by_category)
        binding.tvAlertsTitle.text = getString(R.string.report_top_products)
        binding.btnAction1.text = getString(R.string.report_export)
        binding.btnAction2.text = getString(R.string.nav_payments)
        binding.btnAction1.setOnClickListener { exportCsv() }
        binding.btnAction2.setOnClickListener { host.navigateTo(R.id.nav_admin_orders) }
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
        val orders = Repository.allOrders().filter { Repository.inPeriod(it, periodDays()) }
        val sales = orders.filter { Repository.isSale(it) }
        val revenue = Repository.salesTotal(sales)
        val units = sales.sumOf { o -> o.lines.sumOf { it.quantity } }

        binding.kpi1.tvKpiLabel.text = getString(R.string.kpi_revenue)
        binding.kpi1.tvKpiValue.text = Format.compactMoney(revenue)
        binding.kpi1.tvKpiHint.text = getString(R.string.kpi_units, units)
        binding.kpi2.tvKpiLabel.text = getString(R.string.kpi_paid_orders)
        binding.kpi2.tvKpiValue.text = sales.size.toString()
        binding.kpi2.tvKpiHint.text = getString(R.string.kpi_of_total, orders.size)
        binding.kpi3.tvKpiLabel.text = getString(R.string.kpi_ticket)
        binding.kpi3.tvKpiValue.text = Format.compactMoney(if (sales.isEmpty()) 0L else revenue / sales.size)
        binding.kpi3.tvKpiHint.text = getString(R.string.kpi_shipping_included)
        binding.kpi4.tvKpiLabel.text = getString(R.string.kpi_cancelled)
        binding.kpi4.tvKpiValue.text = orders.count { it.status == OrderStatus.CANCELLED }.toString()
        binding.kpi4.tvKpiHint.text = getString(R.string.kpi_pending_payment, orders.count { it.status == OrderStatus.PENDING_PAYMENT })

        val byCategory = sales.flatMap { it.lines }.groupBy { it.category.label }
            .map { (label, lines) -> label to lines.sumOf { it.subtotal } }
            .sortedByDescending { it.second }
        binding.containerBars.setBars(byCategory.ifEmpty { listOf(getString(R.string.no_sales) to 0L) })

        binding.containerAlerts.removeAllViews()
        val top = sales.flatMap { it.lines }.groupBy { it.title }
            .map { (title, lines) -> Triple(title, lines.sumOf { it.quantity }, lines.sumOf { it.subtotal }) }
            .sortedByDescending { it.third }
            .take(5)
        if (top.isEmpty()) {
            binding.containerAlerts.addRow(R.drawable.ic_chart, R.color.rs_gray_text, getString(R.string.no_sales), getString(R.string.report_no_data))
        }
        top.forEachIndexed { index, (title, qty, value) ->
            binding.containerAlerts.addRow(R.drawable.ic_inventory, R.color.rs_orange, "${index + 1}. $title",
                resources.getQuantityString(R.plurals.units_sold, qty, qty), Format.money(value))
        }
    }

    private fun exportCsv() {
        val admin = (requireActivity() as RoleHomeActivity).user
        val orders = Repository.allOrders().filter { Repository.inPeriod(it, periodDays()) }
        val csv = buildString {
            appendLine("pedido;fecha;estado;metodo_pago;referencia;items;total_cop")
            orders.forEach { o ->
                appendLine(listOf(o.id, o.date, o.status.label, o.paymentMethod, o.paymentRef,
                    o.lines.sumOf { it.quantity }, o.total).joinToString(";"))
            }
        }
        Repository.log(admin.name, "Exportó reporte de ventas", "${orders.size} pedidos · CSV", AuditType.PAYMENTS)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_csv_subject))
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(share, getString(R.string.report_export)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
