package com.rsaldos.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.rsaldos.app.R
import com.rsaldos.app.data.Category
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Grade
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentCatalogBinding
import com.rsaldos.app.ui.common.ProductRowAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.common.toast

class CatalogFragment : Fragment() {
    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductRowAdapter
    private var sort = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductRowAdapter(emptyList(), false, R.drawable.ic_add, ::openDetail) { product, _ -> addToCart(product) }
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = adapter

        binding.chipCategories.addChip(getString(R.string.all), null, checked = true)
        Category.values().forEach { binding.chipCategories.addChip(it.label, it) }
        binding.chipCategories.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.chipGrades.setOnCheckedStateChangeListener { _, _ -> refresh() }

        binding.etSearch.doAfterTextChanged { refresh() }
        binding.tilSearch.setEndIconOnClickListener {
            requireContext().toast(getString(R.string.search_by_image_soon))
        }
        binding.btnFilters.setOnClickListener {
            binding.layoutFilters.visible(binding.layoutFilters.visibility != View.VISIBLE)
        }
        binding.sliderPrice.addOnChangeListener { _, _, _ -> refresh() }
        binding.btnClear.setOnClickListener { clearFilters() }
        binding.tvSort.setOnClickListener { showSortMenu() }
        updateSortLabel()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val grades = mutableSetOf<Grade>()
        if (binding.chipGradeA.isChecked) grades += Grade.A
        if (binding.chipGradeB.isChecked) grades += Grade.B
        if (binding.chipGradeC.isChecked) grades += Grade.C
        val maxPrice = binding.sliderPrice.value.toLong()
        binding.tvPriceLabel.text = getString(R.string.filters_price, Format.money(maxPrice))

        val results = Repository.search(
            query = binding.etSearch.text?.toString().orEmpty(),
            category = binding.chipCategories.checkedTag() as? Category,
            grades = grades,
            maxPrice = if (maxPrice >= 3_000_000L) null else maxPrice,
            sort = sort
        )
        adapter.submit(results)
        binding.tvResults.text = resources.getQuantityString(R.plurals.results_count, results.size, results.size)
        binding.tvEmpty.visible(results.isEmpty())
    }

    private fun clearFilters() {
        binding.chipGrades.clearCheck()
        binding.sliderPrice.value = 3_000_000f
        sort = 0
        updateSortLabel()
        refresh()
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.tvSort)
        val labels = resources.getStringArray(R.array.sort_options)
        labels.forEachIndexed { index, label -> popup.menu.add(0, index, index, label) }
        popup.setOnMenuItemClickListener { item ->
            sort = item.itemId
            updateSortLabel()
            refresh()
            true
        }
        popup.show()
    }

    private fun updateSortLabel() {
        binding.tvSort.text = getString(R.string.sort_label, resources.getStringArray(R.array.sort_options)[sort])
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
