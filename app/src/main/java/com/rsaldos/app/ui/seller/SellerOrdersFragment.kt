package com.rsaldos.app.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Order
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.DialogDispatchBinding
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.common.OrderAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible

class SellerOrdersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = (requireActivity() as RoleHomeActivity).user
        adapter = OrderAdapter(emptyList(), { o ->
            when (o.status) {
                OrderStatus.PAID, OrderStatus.PREPARING -> getString(R.string.order_prepare)
                else -> getString(R.string.order_view)
            }
        }, sellerId = user.id) { onOrder(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(getString(R.string.seller_to_dispatch), FILTER_PENDING, checked = true)
        binding.chipFilters.addChip(getString(R.string.seller_dispatched), FILTER_SENT)
        binding.chipFilters.addChip(getString(R.string.all), FILTER_ALL)
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.tvEmpty.text = getString(R.string.orders_empty_seller)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val user = (requireActivity() as RoleHomeActivity).user
        val all = Repository.ordersOfSeller(user.id)
        val list = when (binding.chipFilters.checkedTag()) {
            FILTER_PENDING -> all.filter { it.status == OrderStatus.PAID || it.status == OrderStatus.PREPARING }
            FILTER_SENT -> all.filter { it.status == OrderStatus.SHIPPED || it.status == OrderStatus.DELIVERED }
            else -> all
        }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    private fun buyerInfo(order: Order): String {
        val buyer = Repository.findUser(order.buyerId)
        val name = buyer?.name?.let { it.substringBefore(" ") + " " + it.substringAfter(" ", "").take(1) + "." } ?: "—"
        val email = buyer?.email?.let { Format.maskEmail(it) } ?: "—"
        return getString(R.string.dispatch_buyer, name, email, order.address.substringAfterLast(",").trim())
    }

    private fun onOrder(order: Order) {
        val user = (requireActivity() as RoleHomeActivity).user
        val lines = order.lines.filter { it.sellerId == user.id }
            .joinToString("\n") { "• ${it.quantity} × ${it.title} (${it.grade.label})" }

        if (order.status != OrderStatus.PAID && order.status != OrderStatus.PREPARING) {
            val tracking = if (order.tracking.isNotBlank()) getString(R.string.order_tracking_line, order.carrier, order.tracking) else getString(R.string.order_no_tracking)
            val builder = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.order_number, order.id) + " · " + order.status.label)
                .setMessage(buyerInfo(order) + "\n\n" + lines + "\n\n" + tracking)
                .setPositiveButton(R.string.action_close, null)
            if (order.status == OrderStatus.SHIPPED) {
                builder.setNeutralButton(R.string.order_mark_delivered) { _, _ ->
                    Repository.setOrderStatus(user, order, OrderStatus.DELIVERED)
                    refresh()
                }
            }
            builder.show()
            return
        }

        val d = DialogDispatchBinding.inflate(layoutInflater)
        d.tvOrderInfo.text = buyerInfo(order) + "\n\n" + lines
        val carriers = resources.getStringArray(R.array.carriers)
        d.actCarrier.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, carriers))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dispatch_title, order.id))
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.dispatch_start, null)
            .setPositiveButton(R.string.dispatch_confirm, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                Repository.setOrderStatus(user, order, OrderStatus.PREPARING)
                requireContext().toast(getString(R.string.dispatch_preparing))
                dialog.dismiss()
                refresh()
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val carrier = d.actCarrier.text?.toString().orEmpty()
                val tracking = d.etTracking.text?.toString()?.trim().orEmpty()
                d.tilCarrier.error = null
                d.tilTracking.error = null
                when {
                    !(d.cbVerified.isChecked && d.cbPacked.isChecked && d.cbInvoice.isChecked) ->
                        requireContext().toast(getString(R.string.dispatch_error_checklist))
                    carrier.isBlank() -> d.tilCarrier.error = getString(R.string.error_required)
                    tracking.length < 6 -> d.tilTracking.error = getString(R.string.dispatch_error_tracking)
                    else -> {
                        Repository.dispatchOrder(user, order, carrier, tracking)
                        requireContext().toast(getString(R.string.dispatch_done, order.id))
                        dialog.dismiss()
                        refresh()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_PENDING = "pending"
        private const val FILTER_SENT = "sent"
        private const val FILTER_ALL = "all"
    }
}
