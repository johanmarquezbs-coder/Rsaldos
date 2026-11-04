package com.rsaldos.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.buyer.ProductDetailActivity
import com.rsaldos.app.ui.common.ProductRowAdapter
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.askText
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import com.rsaldos.app.ui.seller.ProductFormActivity

class AdminProductsFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductRowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductRowAdapter(emptyList(), true, R.drawable.ic_more, { review(it) }) { p, anchor -> showMenu(p, anchor) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(ProductStatus.REVIEW.label, ProductStatus.REVIEW, checked = true)
        binding.chipFilters.addChip(ProductStatus.PUBLISHED.label, ProductStatus.PUBLISHED)
        binding.chipFilters.addChip(ProductStatus.REJECTED.label, ProductStatus.REJECTED)
        binding.chipFilters.addChip(ProductStatus.PAUSED.label, ProductStatus.PAUSED)
        binding.chipFilters.addChip(getString(R.string.all), null)
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }

        binding.fabAdd.visible(true)
        binding.fabAdd.text = getString(R.string.nav_new_product)
        binding.fabAdd.setOnClickListener { startActivity(Intent(requireContext(), ProductFormActivity::class.java)) }
        binding.tvEmpty.text = getString(R.string.products_empty_admin)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val status = binding.chipFilters.checkedTag() as? ProductStatus
        val list = Repository.allProducts().filter { status == null || it.status == status }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    private fun review(p: Product) {
        val seller = Repository.findUser(p.sellerId)
        val message = getString(
            R.string.admin_review_message,
            seller?.storeName?.ifBlank { null } ?: seller?.name ?: "—",
            p.condition.label, p.grade.label, Format.money(p.price), p.stock, p.serial,
            p.warrantyMonths, p.photos, p.tests, p.defects
        )
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(p.title)
            .setMessage(message)
            .setNeutralButton(R.string.admin_preview) { _, _ ->
                startActivity(Intent(requireContext(), ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_ID, p.id))
            }
        if (p.status != ProductStatus.PUBLISHED) builder.setPositiveButton(R.string.action_approve) { _, _ -> approve(p) }
        if (p.status != ProductStatus.REJECTED) builder.setNegativeButton(R.string.action_reject) { _, _ -> reject(p) }
        builder.show()
    }

    private fun approve(p: Product) {
        val admin = (requireActivity() as RoleHomeActivity).user
        Repository.approveProduct(admin, p)
        requireContext().toast(getString(R.string.product_approved))
        refresh()
    }

    private fun reject(p: Product) {
        val admin = (requireActivity() as RoleHomeActivity).user
        requireContext().askText(getString(R.string.action_reject), getString(R.string.admin_reject_message), getString(R.string.field_reason), getString(R.string.action_reject)) { reason ->
            Repository.rejectProduct(admin, p, reason)
            requireContext().toast(getString(R.string.product_rejected))
            refresh()
        }
    }

    private fun showMenu(p: Product, anchor: View) {
        val admin = (requireActivity() as RoleHomeActivity).user
        val popup = PopupMenu(requireContext(), anchor)
        if (p.status != ProductStatus.PUBLISHED) popup.menu.add(0, 1, 0, R.string.action_approve)
        if (p.status != ProductStatus.REJECTED) popup.menu.add(0, 2, 1, R.string.action_reject)
        popup.menu.add(0, 3, 2, R.string.action_edit)
        popup.menu.add(0, 4, 3, if (p.status == ProductStatus.PAUSED) R.string.action_resume else R.string.action_pause)
        popup.menu.add(0, 5, 4, R.string.action_delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> approve(p)
                2 -> reject(p)
                3 -> startActivity(Intent(requireContext(), ProductFormActivity::class.java).putExtra(ProductFormActivity.EXTRA_ID, p.id))
                4 -> { Repository.togglePause(admin, p); refresh() }
                5 -> requireContext().confirm(getString(R.string.product_delete_title), getString(R.string.product_delete_message, p.title), getString(R.string.action_delete)) {
                    Repository.deleteProduct(admin, p)
                    requireContext().toast(getString(R.string.product_deleted))
                    refresh()
                }
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
