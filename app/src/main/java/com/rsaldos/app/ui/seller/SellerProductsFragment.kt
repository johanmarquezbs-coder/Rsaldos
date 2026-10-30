package com.rsaldos.app.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rsaldos.app.R
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.common.ProductRowAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class SellerProductsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductRowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductRowAdapter(emptyList(), true, R.drawable.ic_more, { openForm(it.id) }) { product, anchor ->
            showMenu(product, anchor)
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(getString(R.string.all), null, checked = true)
        ProductStatus.values().forEach { binding.chipFilters.addChip(it.label, it) }
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }

        binding.tilSearch.visible(true)
        binding.tilSearch.hint = getString(R.string.search_my_products)
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = refresh()
        })

        binding.fabAdd.visible(true)
        binding.fabAdd.text = getString(R.string.nav_new_product)
        binding.fabAdd.setOnClickListener { startActivity(Intent(requireContext(), ProductFormActivity::class.java)) }
        binding.tvEmpty.text = getString(R.string.products_empty)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val user = (requireActivity() as RoleHomeActivity).user
        val status = binding.chipFilters.checkedTag() as? ProductStatus
        val query = binding.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val list = Repository.productsOfSeller(user.id)
            .filter { status == null || it.status == status }
            .filter { query.isEmpty() || it.title.lowercase().contains(query) || it.serial.lowercase().contains(query) }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    private fun showMenu(product: Product, anchor: View) {
        val user = (requireActivity() as RoleHomeActivity).user
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.action_edit)
        popup.menu.add(0, 2, 1, if (product.status == ProductStatus.PAUSED) R.string.action_resume else R.string.action_pause)
        popup.menu.add(0, 3, 2, R.string.action_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openForm(product.id)
                2 -> {
                    Repository.togglePause(user, product)
                    refresh()
                }
                3 -> requireContext().confirm(
                    getString(R.string.product_delete_title),
                    getString(R.string.product_delete_message, product.title),
                    getString(R.string.action_delete)
                ) {
                    Repository.deleteProduct(user, product)
                    requireContext().toast(getString(R.string.product_deleted))
                    refresh()
                }
            }
            true
        }
        popup.show()
    }

    private fun openForm(id: Int) {
        startActivity(Intent(requireContext(), ProductFormActivity::class.java).putExtra(ProductFormActivity.EXTRA_ID, id))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
