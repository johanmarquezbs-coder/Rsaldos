package com.rsaldos.app.ui.common

import android.util.Patterns

object Validators {
    fun isEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    fun isStrongPassword(value: String): Boolean =
        value.length >= 8 && value.any { it.isUpperCase() } && value.any { it.isLowerCase() } && value.any { it.isDigit() }

    fun isPhone(value: String): Boolean = value.filter { it.isDigit() }.length in 7..12
}
