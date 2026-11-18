package com.rsaldos.app.ui.admin

import com.rsaldos.app.ui.common.Refreshable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.data.EpaycoService
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.ui.common.progressDialog
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Order
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.common.OrderAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.askText
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible

/**
 * Pagos y conciliación del administrador: lista todos los pedidos, concilia con ePayco los
 * pendientes, cancela pedidos sin pago y registra reembolsos.
 */
class AdminOrdersFragment : Fragment(), Refreshable {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = OrderAdapter(emptyList(), { o ->
            when (o.status) {
                OrderStatus.PENDING_PAYMENT -> getString(R.string.admin_reconcile)
                OrderStatus.PAID -> getString(R.string.admin_verify)
                else -> getString(R.string.order_view)
            }
        }) { manage(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(getString(R.string.admin_to_reconcile), FILTER_RECONCILE, checked = true)
        binding.chipFilters.addChip(getString(R.string.orders_in_progress), FILTER_PROGRESS)
        binding.chipFilters.addChip(getString(R.string.admin_closed), FILTER_CLOSED)
        binding.chipFilters.addChip(getString(R.string.all), FILTER_ALL)
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.tvEmpty.text = getString(R.string.orders_empty)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Vuelve a leer los datos sincronizados con Firestore y redibuja la pantalla. */
    override fun refresh() {
        val all = Repository.allOrders()
        val list = when (binding.chipFilters.checkedTag()) {
            FILTER_RECONCILE -> all.filter { it.status == OrderStatus.PENDING_PAYMENT || it.status == OrderStatus.PAID }
            FILTER_PROGRESS -> all.filter { it.status == OrderStatus.PREPARING || it.status == OrderStatus.SHIPPED }
            FILTER_CLOSED -> all.filter { it.status == OrderStatus.DELIVERED || it.status == OrderStatus.CANCELLED }
            else -> all
        }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    /** Detalle del pedido con el estado de la pasarela y las acciones disponibles. */
    private fun manage(order: Order) {
        val admin = (requireActivity() as RoleHomeActivity).user
        val gatewayState = when (order.status) {
            OrderStatus.PENDING_PAYMENT -> getString(R.string.gateway_pending)
            OrderStatus.CANCELLED -> getString(R.string.gateway_rejected)
            else -> getString(R.string.gateway_approved)
        }
        val message = getString(
            R.string.admin_order_message, order.buyerName.ifBlank { "—" }, order.date, order.paymentMethod, order.paymentRef,
            gatewayState, order.status.label, Format.money(order.total)
        )
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_number, order.id))
            .setMessage(message)
            .setNegativeButton(R.string.action_close, null)
        when (order.status) {
            OrderStatus.PENDING_PAYMENT -> {
                builder.setPositiveButton(R.string.admin_confirm_payment) { _, _ -> reconcile(admin, order) }
                builder.setNeutralButton(R.string.order_cancel) { _, _ ->
                    Repository.setOrderStatus(admin, order, OrderStatus.CANCELLED)
                    refresh()
                }
            }
            OrderStatus.PAID, OrderStatus.PREPARING -> builder.setPositiveButton(R.string.admin_refund) { _, _ ->
                requireContext().askText(getString(R.string.admin_refund), getString(R.string.admin_refund_message, Format.money(order.total)),
                    getString(R.string.field_reason), getString(R.string.admin_refund)) { reason ->
                    Repository.setOrderStatus(admin, order, OrderStatus.CANCELLED)
                    Repository.log(admin.name, "Reembolso", "${order.id} · ${Format.money(order.total)} · $reason", AuditType.PAYMENTS)
                    requireContext().toast(getString(R.string.admin_refunded))
                    refresh()
                }
            }
            else -> Unit
        }
        builder.show()
    }

    /**
     * Conciliación real: consulta en ePayco el estado de la transacción del pedido por su
     * referencia (`ref_payco`) y aplica el resultado (aprobado descuenta inventario).
     */
    private fun reconcile(admin: com.rsaldos.app.data.User, order: Order) {
        val ctx = requireContext()
        if (order.paymentRef.isBlank()) {
            ctx.toast(getString(R.string.admin_no_reference))
            return
        }
        val progress = ctx.progressDialog(getString(R.string.epayco_validating))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tx = EpaycoService.verify(order, EpaycoService.validate(order.paymentRef))
                Repository.confirmPayment(order.id, tx)
                Repository.log(admin.name, "Conciliación", "${order.id} · ePayco: ${tx.responseText}", AuditType.PAYMENTS)
                ctx.toast(getString(R.string.admin_reconciled_result, tx.responseText.ifBlank { tx.result.name }))
            } catch (e: Exception) {
                ctx.toast(getString(e.userMessage()))
            } finally {
                progress.dismiss()
                if (_binding != null) refresh()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_RECONCILE = "reconcile"
        private const val FILTER_PROGRESS = "progress"
        private const val FILTER_CLOSED = "closed"
        private const val FILTER_ALL = "all"
    }
}
