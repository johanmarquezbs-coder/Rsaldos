package com.rsaldos.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.rsaldos.app.R
import com.rsaldos.app.data.Category
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentBuyerHomeBinding
import com.rsaldos.app.ui.common.ProductGridAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.toast

class BuyerHomeFragment : Fragment() {
    private var _binding: FragmentBuyerHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuyerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as RoleHomeActivity
        val user = host.user
        binding.tvAddress.text = if (user.address.isNotBlank()) "${user.address}, ${user.city}" else getString(R.string.home_no_address)

        adapter = ProductGridAdapter(emptyList(), ::openDetail, ::addToCart)
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        binding.chipCategories.addChip(getString(R.string.all), null, checked = true)
        Category.values().forEach { binding.chipCategories.addChip(it.label, it) }
        binding.chipCategories.setOnCheckedStateChangeListener { _, _ -> refresh() }

        binding.searchBox.setOnClickListener { host.navigateTo(R.id.nav_catalog) }
        binding.tvSeeAll.setOnClickListener { host.navigateTo(R.id.nav_catalog) }
        binding.btnPromo.setOnClickListener { host.navigateTo(R.id.nav_catalog) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val category = binding.chipCategories.checkedTag() as? Category
        val list = Repository.publishedProducts()
            .filter { category == null || it.category == category }
            .sortedByDescending { it.discountPercent }
        adapter.submit(list)
    }

    private fun openDetail(product: Product) {
        startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
            .putExtra(ProductDetailActivity.EXTRA_ID, product.id))
    }

    private fun addToCart(product: Product) {
        if (Repository.addToCart(product)) {
            requireContext().toast(getString(R.string.cart_added, product.title))
            (requireActivity() as RoleHomeActivity).updateCartBadge()
        } else {
            requireContext().toast(getString(R.string.cart_no_stock))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
