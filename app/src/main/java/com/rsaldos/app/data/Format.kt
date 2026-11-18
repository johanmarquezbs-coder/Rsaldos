package com.rsaldos.app.data

import java.util.Locale

/** Formatos de presentación en pesos colombianos y enmascaramiento de datos personales. */
object Format {
    /** Valor en pesos con separador de miles, p. ej. $1.349.000. */
    fun money(value: Long): String = "$" + String.format(Locale.US, "%,d", value).replace(',', '.')

    /** Valor abreviado para los indicadores, p. ej. $1,3 M o $450 mil. */
    fun compactMoney(value: Long): String = when {
        value >= 1_000_000 -> "$" + String.format(Locale.US, "%.1f", value / 1_000_000.0).replace('.', ',') + " M"
        value >= 1_000 -> "$" + (value / 1_000) + " mil"
        else -> money(value)
    }

    /** Enmascara el correo (l***@dominio) para mostrarlo al vendedor. */
    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].isEmpty()) return email
        return parts[0].first() + "***@" + parts[1]
    }
}
