package com.rsaldos.app.ui.admin

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
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.data.UserStatus
import com.rsaldos.app.databinding.FragmentListBinding
import com.rsaldos.app.ui.common.RoleHomeActivity
import com.rsaldos.app.ui.common.UserAdapter
import com.rsaldos.app.ui.common.addChip
import com.rsaldos.app.ui.common.askText
import com.rsaldos.app.ui.common.checkedTag
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible

class AdminUsersFragment : Fragment() {
    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = UserAdapter(emptyList(), { openForm(it) }) { user, anchor -> showMenu(user, anchor) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter

        binding.tilSearch.visible(true)
        binding.tilSearch.hint = getString(R.string.users_search)
        binding.etSearch.doAfterTextChanged { refresh() }

        binding.chipFilters.addChip(getString(R.string.all), FILTER_ALL, checked = true)
        binding.chipFilters.addChip(getString(R.string.users_buyers), Role.BUYER)
        binding.chipFilters.addChip(getString(R.string.users_sellers), Role.SELLER)
        binding.chipFilters.addChip(getString(R.string.users_admins), Role.ADMIN)
        binding.chipFilters.addChip(getString(R.string.users_pending), UserStatus.PENDING)
        binding.chipFilters.addChip(getString(R.string.users_blocked), UserStatus.BLOCKED)
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> refresh() }

        binding.fabAdd.visible(true)
        binding.fabAdd.text = getString(R.string.admin_new_user)
        binding.fabAdd.setOnClickListener { startActivity(Intent(requireContext(), UserFormActivity::class.java)) }
        binding.tvEmpty.text = getString(R.string.users_empty)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = binding.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val filter = binding.chipFilters.checkedTag()
        val list = Repository.allUsers()
            .filter {
                when (filter) {
                    is Role -> it.role == filter
                    is UserStatus -> it.status == filter
                    else -> true
                }
            }
            .filter {
                query.isEmpty() || it.name.lowercase().contains(query) || it.email.lowercase().contains(query) ||
                    it.document.lowercase().contains(query)
            }
        adapter.submit(list)
        binding.tvCount.text = resources.getQuantityString(R.plurals.users_count, list.size, list.size)
        binding.tvEmpty.visible(list.isEmpty())
    }

    private fun showMenu(target: User, anchor: View) {
        val admin = (requireActivity() as RoleHomeActivity).user
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.action_edit)
        if (target.status == UserStatus.PENDING) popup.menu.add(0, 2, 1, R.string.users_approve_seller)
        if (target.id != admin.id) {
            popup.menu.add(0, 3, 2, if (target.status == UserStatus.BLOCKED) R.string.users_unblock else R.string.users_block)
            popup.menu.add(0, 4, 3, R.string.action_delete)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openForm(target)
                2 -> withReason(R.string.users_approve_seller) { reason ->
                    target.status = UserStatus.ACTIVE
                    Repository.updateUser(admin, target, reason)
                }
                3 -> withReason(if (target.status == UserStatus.BLOCKED) R.string.users_unblock else R.string.users_block) { reason ->
                    target.status = if (target.status == UserStatus.BLOCKED) UserStatus.ACTIVE else UserStatus.BLOCKED
                    if (target.status == UserStatus.BLOCKED) target.openSessions = 0
                    Repository.updateUser(admin, target, reason)
                }
                4 -> withReason(R.string.users_delete_title) { reason ->
                    Repository.deleteUser(admin, target, reason)
                }
            }
            true
        }
        popup.show()
    }

    private fun withReason(titleRes: Int, action: (String) -> Unit) {
        requireContext().askText(getString(titleRes), getString(R.string.users_reason_message), getString(R.string.user_reason), getString(R.string.action_confirm)) { reason ->
            action(reason)
            requireContext().toast(getString(R.string.users_updated))
            refresh()
        }
    }

    private fun openForm(user: User) {
        startActivity(Intent(requireContext(), UserFormActivity::class.java).putExtra(UserFormActivity.EXTRA_ID, user.id))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_ALL = "all"
    }
}
