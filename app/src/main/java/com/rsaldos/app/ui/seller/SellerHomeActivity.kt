package com.rsaldos.app.ui.seller

import android.content.Intent
import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.ui.common.ProfileFragment
import com.rsaldos.app.ui.common.RoleHomeActivity

class SellerHomeActivity : RoleHomeActivity() {
    override val bottomMenu = R.menu.bottom_seller
    override val drawerMenu = R.menu.drawer_seller
    override val startDestination = R.id.nav_seller_dashboard

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_seller_dashboard -> SellerDashboardFragment()
        R.id.nav_seller_products -> SellerProductsFragment()
        R.id.nav_seller_orders -> SellerOrdersFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }

    override fun onDrawerAction(itemId: Int): Boolean {
        if (itemId == R.id.nav_seller_new) {
            startActivity(Intent(this, ProductFormActivity::class.java))
            return true
        }
        return false
    }
}
