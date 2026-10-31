package com.rsaldos.app.ui.admin

import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.ui.common.ProfileFragment
import com.rsaldos.app.ui.common.RoleHomeActivity

class AdminHomeActivity : RoleHomeActivity() {
    override val bottomMenu = R.menu.bottom_admin
    override val drawerMenu = R.menu.drawer_admin
    override val startDestination = R.id.nav_admin_dashboard

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_admin_dashboard -> AdminDashboardFragment()
        R.id.nav_admin_users -> AdminUsersFragment()
        R.id.nav_admin_products -> AdminProductsFragment()
        R.id.nav_admin_reports -> SalesReportFragment()
        R.id.nav_admin_audit -> AuditFragment()
        R.id.nav_admin_orders -> AdminOrdersFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }
}
