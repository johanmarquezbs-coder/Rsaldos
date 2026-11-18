package com.rsaldos.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import coil.dispose
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.rsaldos.app.R
import com.rsaldos.app.data.Product
import com.rsaldos.app.databinding.DialogInputBinding

/**
 * Funciones de extensión compartidas por todas las pantallas: colores, mensajes, insignias,
 * avatares, diálogos de confirmación y de texto, chips, barras de gráficas, filas simples,
 * carga de fotos de producto y diálogo de progreso.
 */

/** Pantallas que se redibujan cuando llegan datos nuevos desde Firestore. */
interface Refreshable {
    /** Vuelve a leer la caché del repositorio y actualiza la vista. */
    fun refresh()
}

/** Obtiene un color de los recursos. */
fun Context.color(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

/** Muestra un mensaje corto. */
fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/** Muestra un mensaje corto a partir de un recurso de texto. */
fun Context.toast(@androidx.annotation.StringRes message: Int) = toast(getString(message))

/** Muestra u oculta la vista (VISIBLE / GONE). */
fun View.visible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/** Convierte el TextView en una insignia de color con texto. */
fun TextView.badge(text: String, @ColorRes textColor: Int, @ColorRes bgColor: Int) {
    this.text = text
    setTextColor(context.color(textColor))
    backgroundTintList = ColorStateList.valueOf(context.color(bgColor))
    visibility = View.VISIBLE
}

/** Pinta un avatar circular con iniciales. */
fun TextView.avatar(initials: String, @ColorRes bgColor: Int) {
    text = initials
    setTextColor(context.color(R.color.rs_white))
    backgroundTintList = ColorStateList.valueOf(context.color(bgColor))
}

/** Cambia el color de tinte de un ícono. */
fun ImageView.tint(@ColorRes res: Int) {
    imageTintList = ColorStateList.valueOf(context.color(res))
}

/** Color estable de avatar según el id del usuario. */
fun avatarColor(id: String): Int {
    val colors = intArrayOf(
        R.color.rs_orange, R.color.rs_graphite, R.color.rs_blue,
        R.color.rs_green, R.color.rs_purple, R.color.rs_teal
    )
    return colors[Math.abs(id.hashCode() % colors.size)]
}

/** Diálogo de confirmación con botón de cancelar. */
fun Context.confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(positive) { _, _ -> onConfirm() }
        .show()
}

/**
 * Diálogo que pide un texto obligatorio (motivo, código, contraseña) y lo entrega en [onText].
 * Con [password] el campo oculta lo que se escribe.
 */
fun Context.askText(
    title: String,
    message: String,
    hint: String,
    positive: String,
    numeric: Boolean = false,
    password: Boolean = false,
    onText: (String) -> Unit
) {
    val dialogBinding = DialogInputBinding.inflate(LayoutInflater.from(this))
    dialogBinding.tvDialogMessage.text = message
    dialogBinding.tilDialogInput.hint = hint
    if (numeric) dialogBinding.etDialogInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    if (password) {
        dialogBinding.etDialogInput.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        dialogBinding.tilDialogInput.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(dialogBinding.root)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(positive, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val raw = dialogBinding.etDialogInput.text?.toString().orEmpty()
            val value = if (password) raw else raw.trim()
            if (value.isEmpty()) {
                dialogBinding.tilDialogInput.error = getString(R.string.error_required)
            } else {
                dialog.dismiss()
                onText(value)
            }
        }
    }
    dialog.show()
}

/** Agrega un chip seleccionable con una etiqueta y un valor asociado (tag). */
fun com.google.android.material.chip.ChipGroup.addChip(
    label: String,
    tag: Any?,
    checked: Boolean = false
): com.google.android.material.chip.Chip {
    val chip = com.google.android.material.chip.Chip(context)
    chip.id = View.generateViewId()
    chip.text = label
    chip.tag = tag
    chip.isCheckable = true
    addView(chip)
    if (checked) check(chip.id)
    return chip
}

/** Devuelve el tag del chip seleccionado. */
fun com.google.android.material.chip.ChipGroup.checkedTag(): Any? =
    findViewById<com.google.android.material.chip.Chip>(checkedChipId)?.tag

/** Dibuja una gráfica de barras horizontales con etiquetas y valores en pesos. */
fun android.widget.LinearLayout.setBars(data: List<Pair<String, Long>>) {
    removeAllViews()
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val inflater = LayoutInflater.from(context)
    data.forEach { (label, value) ->
        val row = com.rsaldos.app.databinding.ItemBarBinding.inflate(inflater, this, false)
        row.tvBarLabel.text = label
        row.pbBar.progress = ((value * 100) / max).toInt()
        row.tvBarValue.text = com.rsaldos.app.data.Format.compactMoney(value)
        addView(row.root)
    }
}

/** Agrega una fila con ícono, título, subtítulo y texto final (alertas y listas simples). */
fun android.widget.LinearLayout.addRow(
    icon: Int,
    @ColorRes iconColor: Int,
    title: String,
    subtitle: String,
    trailing: String = "",
    onClick: (() -> Unit)? = null
) {
    val row = com.rsaldos.app.databinding.ItemSimpleRowBinding.inflate(LayoutInflater.from(context), this, false)
    row.ivRowIcon.setImageResource(icon)
    row.ivRowIcon.tint(iconColor)
    row.tvRowTitle.text = title
    row.tvRowSubtitle.text = subtitle
    row.tvRowTrailing.text = trailing
    if (onClick != null) row.root.setOnClickListener { onClick() }
    addView(row.root)
}

/**
 * Carga una foto del producto guardada en Firestore con Coil, ocupando todo el recuadro.
 * Si el producto no tiene fotos, muestra el ícono de la categoría con su tamaño y tinte
 * originales (el tamaño se recuerda en un tag para que funcione al reciclar filas).
 */
@Suppress("UNCHECKED_CAST")
fun ImageView.loadProduct(product: Product, index: Int = 0) {
    val url = product.photoUrls.getOrNull(index)
    val params = layoutParams
    if (getTag(R.id.tag_icon_size) == null) setTag(R.id.tag_icon_size, params.width to params.height)
    val (iconWidth, iconHeight) = getTag(R.id.tag_icon_size) as Pair<Int, Int>
    dispose()
    if (url == null) {
        params.width = iconWidth
        params.height = iconHeight
        layoutParams = params
        imageTintList = ColorStateList.valueOf(context.color(R.color.rs_graphite_soft))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(product.category.icon)
        return
    }
    params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
    params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
    layoutParams = params
    imageTintList = null
    scaleType = ImageView.ScaleType.CENTER_CROP
    (parent as? View)?.clipToOutline = true
    load(url) {
        crossfade(true)
        error(product.category.icon)
    }
}

/** Muestra un diálogo modal de progreso con un mensaje; devuelve el diálogo para cerrarlo. */
fun Context.progressDialog(message: String): AlertDialog {
    val pad = resources.getDimensionPixelSize(R.dimen.space_l)
    val layout = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(pad, pad, pad, pad)
        addView(CircularProgressIndicator(context).apply { isIndeterminate = true })
        addView(TextView(context).apply {
            text = message
            setPadding(pad, 0, 0, 0)
        })
    }
    return MaterialAlertDialogBuilder(this).setView(layout).setCancelable(false).show()
}

/** Abre en Google Maps la ubicación GPS asociada a la dirección de entrega del pedido. */
fun Context.openOrderMap(order: com.rsaldos.app.data.Order) {
    val lat = order.latitude ?: return
    val lng = order.longitude ?: return
    val uri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(${android.net.Uri.encode(order.id)})")
    try {
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
    } catch (e: android.content.ActivityNotFoundException) {
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")))
    }
}
