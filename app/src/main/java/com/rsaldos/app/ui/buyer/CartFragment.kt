package com.rsaldos.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentCartBinding
import com.rsaldos.app.ui.common.CartAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class CartFragment : Fragment() {
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CartAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CartAdapter(
            emptyList(),
            onQuantity = { item, qty ->
                if (qty < 1) {
                    removeItem(item.product.title) { Repository.removeFromCart(item) }
                } else if (!Repository.updateQuantity(item, qty)) {
                    requireContext().toast(getString(R.string.cart_no_stock))
                } else refresh()
            },
            onDelete = { item -> removeItem(item.product.title) { Repository.removeFromCart(item) } },
            onClick = { item ->
                startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                    .putExtra(ProductDetailActivity.EXTRA_ID, item.product.id))
            }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        binding.btnClear.setOnClickListener {
            requireContext().confirm(getString(R.string.cart_clear_title), getString(R.string.cart_clear_message), getString(R.string.cart_clear)) {
                Repository.clearCart()
                refresh()
            }
        }
        binding.btnCheckout.setOnClickListener {
            startActivity(Intent(requireContext(), CheckoutActivity::class.java))
        }
        binding.btnGoCatalog.setOnClickListener {
            (requireActivity() as RoleHomeActivity).navigateTo(R.id.nav_catalog)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun removeItem(title: String, action: () -> Unit) {
        requireContext().confirm(getString(R.string.cart_remove_title), getString(R.string.cart_remove_message, title), getString(R.string.action_delete)) {
            action()
            refresh()
        }
    }

    private fun refresh() {
        val items = Repository.cart.toList()
        adapter.submit(items)
        val empty = items.isEmpty()
        binding.layoutEmpty.visible(empty)
        binding.layoutSummary.visible(!empty)
        val subtotal = Repository.cartTotal()
        binding.tvSubtotal.text = Format.money(subtotal)
        binding.tvShipping.text = Format.money(Repository.SHIPPING_COST)
        binding.tvTotal.text = Format.money(subtotal + Repository.SHIPPING_COST)
        binding.btnCheckout.text = getString(R.string.cart_checkout_count, Repository.cartCount())
        (activity as? RoleHomeActivity)?.updateCartBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
