package com.rsaldos.app.data

import java.util.Locale

object Format {
    fun money(value: Long): String = "$" + String.format(Locale.US, "%,d", value).replace(',', '.')

    fun compactMoney(value: Long): String = when {
        value >= 1_000_000 -> "$" + String.format(Locale.US, "%.1f", value / 1_000_000.0).replace('.', ',') + " M"
        value >= 1_000 -> "$" + (value / 1_000) + " mil"
        else -> money(value)
    }

    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].isEmpty()) return email
        return parts[0].first() + "***@" + parts[1]
    }
}
