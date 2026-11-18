package com.rsaldos.app.ui.common

import android.app.Activity
import android.content.Intent
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.User
import com.rsaldos.app.ui.admin.AdminHomeActivity
import com.rsaldos.app.ui.auth.LoginActivity
import com.rsaldos.app.ui.auth.SplashActivity
import com.rsaldos.app.ui.buyer.BuyerHomeActivity
import com.rsaldos.app.ui.seller.SellerHomeActivity

/** Navegación entre los módulos de cada rol, limpiando la pila de actividades. */
object Navigator {
    /** Abre la pantalla principal que corresponde al rol del usuario. */
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

    /** Vuelve al inicio de sesión (después de cerrar sesión o si la cuenta fue revocada). */
    fun openLogin(activity: Activity) {
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
    }

    /** Reinicia la app desde la pantalla de inicio para restaurar la sesión (p. ej. tras cerrarse el proceso). */
    fun restart(activity: Activity) {
        val intent = Intent(activity, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
    }
}
