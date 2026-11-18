package com.rsaldos.app.ui.common

import android.util.Patterns

/** Validaciones de formularios compartidas por registro, perfil y administración. */
object Validators {
    /** Correo con formato válido. */
    fun isEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    /** Contraseña de mínimo 8 caracteres con mayúscula, minúscula y número. */
    fun isStrongPassword(value: String): Boolean =
        value.length >= 8 && value.any { it.isUpperCase() } && value.any { it.isLowerCase() } && value.any { it.isDigit() }

    /** Teléfono de 7 a 12 dígitos. */
    fun isPhone(value: String): Boolean = value.filter { it.isDigit() }.length in 7..12
}
