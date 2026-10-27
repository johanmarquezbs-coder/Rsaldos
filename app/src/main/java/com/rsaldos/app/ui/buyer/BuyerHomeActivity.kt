package com.rsaldos.app.ui.buyer

import androidx.fragment.app.Fragment
import com.rsaldos.app.R
import com.rsaldos.app.ui.common.ProfileFragment
import com.rsaldos.app.ui.common.RoleHomeActivity

class BuyerHomeActivity : RoleHomeActivity() {
    override val bottomMenu = R.menu.bottom_buyer
    override val drawerMenu = R.menu.drawer_buyer
    override val startDestination = R.id.nav_home

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_home -> BuyerHomeFragment()
        R.id.nav_catalog -> CatalogFragment()
        R.id.nav_cart -> CartFragment()
        R.id.nav_orders -> BuyerOrdersFragment()
        R.id.nav_profile -> ProfileFragment()
        else -> null
    }
}
