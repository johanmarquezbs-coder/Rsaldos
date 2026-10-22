package com.rsaldos.app.ui.common

import android.app.Activity
import android.content.Intent
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.ui.admin.AdminHomeActivity
import com.rsaldos.app.ui.auth.LoginActivity
import com.rsaldos.app.ui.buyer.BuyerHomeActivity
import com.rsaldos.app.ui.seller.SellerHomeActivity

object Navigator {
    fun openHome(activity: Activity, user: User) {
        val target = when (user.role) {
            Role.BUYER -> BuyerHomeActivity::class.java
            Role.SELLER -> SellerHomeActivity::class.java
            Role.ADMIN -> AdminHomeActivity::class.java
        }
        val intent = Intent(activity, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
    }

    fun openLogin(activity: Activity) {
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
    }
}
