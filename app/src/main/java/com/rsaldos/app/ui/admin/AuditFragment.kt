package com.rsaldos.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.common.AuditAdapter
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.color
import com.rsaldos.app.ui.common.visible

class AuditFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AuditAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AuditAdapter(emptyList())
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        binding.rvList.setBackgroundColor(requireContext().color(R.color.rs_white))
        binding.rvList.adapter = adapter

        binding.chipFilters.addChip(getString(R.string.all), null, checked = true)
        AuditType.values().forEach { binding.chipFilters.addChip(it.label, it) }
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.tvEmpty.text = getString(R.string.audit_empty)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val type = binding.chipFilters.checkedTag() as? AuditType
        val list = Repository.auditLog().filter { type == null || it.type == type }
        adapter.submit(list)
        binding.tvCount.text = getString(R.string.audit_count, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
