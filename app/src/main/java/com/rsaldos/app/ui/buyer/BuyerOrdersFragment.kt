package com.rsaldos.app.ui.buyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class BuyerOrdersFragment : Fragment() {
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
                OrderStatus.SHIPPED -> getString(R.string.order_track)
                OrderStatus.DELIVERED -> getString(R.string.order_warranty)
                else -> getString(R.string.order_view)
            }
        }) { showDetail(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(getString(R.string.all), FILTER_ALL, checked = true)
        binding.chipFilters.addChip(getString(R.string.orders_in_progress), FILTER_ACTIVE)
        binding.chipFilters.addChip(getString(R.string.orders_delivered), FILTER_DONE)
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.tvEmpty.text = getString(R.string.orders_empty)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val user = (requireActivity() as RoleHomeActivity).user
        val all = Repository.ordersOfBuyer(user.id)
        val list = when (binding.chipFilters.checkedTag()) {
            FILTER_ACTIVE -> all.filter { it.status in listOf(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.PENDING_PAYMENT) }
            FILTER_DONE -> all.filter { it.status == OrderStatus.DELIVERED }
            else -> all
        }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    private fun showDetail(order: Order) {
        val ctx = requireContext()
        val user = (requireActivity() as RoleHomeActivity).user
        val lines = order.lines.joinToString("\n") { "• ${it.quantity} × ${it.title} (${it.grade.label}) — ${Format.money(it.subtotal)}" }
        val tracking = if (order.tracking.isNotBlank()) getString(R.string.order_tracking_line, order.carrier, order.tracking) else getString(R.string.order_no_tracking)
        val message = getString(
            R.string.order_detail_message, lines, Format.money(order.shipping), Format.money(order.total),
            order.address, order.paymentMethod, order.paymentRef, tracking
        )
        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.order_number, order.id) + " · " + order.status.label)
            .setMessage(message)
            .setPositiveButton(R.string.action_close, null)
        when (order.status) {
            OrderStatus.PAID, OrderStatus.PENDING_PAYMENT -> builder.setNegativeButton(R.string.order_cancel) { _, _ ->
                ctx.confirm(getString(R.string.order_cancel), getString(R.string.order_cancel_message), getString(R.string.order_cancel)) {
                    Repository.setOrderStatus(user, order, OrderStatus.CANCELLED)
                    ctx.toast(getString(R.string.order_cancelled))
                    refresh()
                }
            }
            OrderStatus.DELIVERED -> builder.setNegativeButton(R.string.order_warranty) { _, _ ->
                ctx.askText(getString(R.string.order_warranty), getString(R.string.order_warranty_message), getString(R.string.field_reason), getString(R.string.action_send)) { reason ->
                    Repository.log(user.name, "Solicitud de garantía/devolución", "${order.id} · $reason", AuditType.ORDERS)
                    ctx.toast(getString(R.string.order_warranty_sent))
                }
            }
            else -> Unit
        }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_ALL = "all"
        private const val FILTER_ACTIVE = "active"
        private const val FILTER_DONE = "done"
    }
}
