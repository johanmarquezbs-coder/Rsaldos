package com.rsaldos.app.ui.common

import android.os.Bundle
import android.view.Menu
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.AuditType
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.databinding.ActivityRoleHomeBinding
import com.rsaldos.app.databinding.NavHeaderBinding

abstract class RoleHomeActivity : AppCompatActivity() {
    protected lateinit var binding: ActivityRoleHomeBinding
    lateinit var session: SessionManager
    lateinit var user: User

    @get:MenuRes protected abstract val bottomMenu: Int
    @get:MenuRes protected abstract val drawerMenu: Int
    @get:IdRes protected abstract val startDestination: Int

    protected abstract fun fragmentFor(@IdRes itemId: Int): Fragment?

    protected open fun onDrawerAction(@IdRes itemId: Int): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        val current = session.currentUser()
        if (current == null) {
            Navigator.openLogin(this)
            finish()
            return
        }
        user = current

        binding = ActivityRoleHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.inflateMenu(bottomMenu)
        binding.navView.inflateMenu(drawerMenu)
        bindHeader()

        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        if (savedInstanceState == null) {
            val first = intent.getIntExtra(EXTRA_TAB, startDestination)
            binding.bottomNav.selectedItemId = first
            show(first)
        } else {
            binding.toolbar.title = binding.bottomNav.menu.findItem(binding.bottomNav.selectedItemId)?.title
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { /* sin acción: evita recargar */ }

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_logout -> confirmLogout()
                R.id.nav_help -> showHelp()
                else -> if (!onDrawerAction(item.itemId)) navigateTo(item.itemId)
            }
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.bottomNav.selectedItemId != startDestination ->
                        binding.bottomNav.selectedItemId = startDestination
                    else -> finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) updateCartBadge()
    }

    private fun bindHeader() {
        val header = NavHeaderBinding.bind(binding.navView.getHeaderView(0))
        header.tvHeaderAvatar.text = user.initials
        header.tvHeaderAvatar.setTextColor(color(R.color.rs_orange))
        header.tvHeaderName.text = user.name
        header.tvHeaderEmail.text = user.email
        header.tvHeaderRole.badge(user.role.label, R.color.rs_orange_dark, R.color.rs_white)
    }

    fun navigateTo(@IdRes itemId: Int) {
        if (binding.bottomNav.menu.findItem(itemId) != null) {
            binding.bottomNav.selectedItemId = itemId
        } else {
            show(itemId)
        }
    }

    private fun show(@IdRes itemId: Int) {
        val fragment = fragmentFor(itemId) ?: return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        binding.toolbar.title = findTitle(binding.navView.menu, itemId)
            ?: findTitle(binding.bottomNav.menu, itemId)
        binding.navView.setCheckedItem(itemId)
    }

    private fun findTitle(menu: Menu, itemId: Int): CharSequence? = menu.findItem(itemId)?.title

    fun updateCartBadge() {
        if (binding.bottomNav.menu.findItem(R.id.nav_cart) == null) return
        val count = Repository.cartCount()
        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
        badge.backgroundColor = color(R.color.rs_orange)
        badge.badgeTextColor = color(R.color.rs_white)
        badge.setVisible(count > 0)
        badge.number = count
    }

    fun confirmLogout() {
        confirm(getString(R.string.logout_title), getString(R.string.logout_message), getString(R.string.nav_logout)) {
            Repository.log(user.name, "Cierre de sesión", "Rol ${user.role.label}", AuditType.SECURITY)
            session.logout()
            Navigator.openLogin(this)
            finish()
        }
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_message)
            .setPositiveButton(R.string.action_understood, null)
            .show()
    }

    companion object {
        const val EXTRA_TAB = "start_tab"
    }
}
