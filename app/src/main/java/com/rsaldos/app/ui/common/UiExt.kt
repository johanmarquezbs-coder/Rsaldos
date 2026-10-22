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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.databinding.DialogInputBinding

fun Context.color(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun View.visible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun TextView.badge(text: String, @ColorRes textColor: Int, @ColorRes bgColor: Int) {
    this.text = text
    setTextColor(context.color(textColor))
    backgroundTintList = ColorStateList.valueOf(context.color(bgColor))
    visibility = View.VISIBLE
}

fun TextView.avatar(initials: String, @ColorRes bgColor: Int) {
    text = initials
    setTextColor(context.color(R.color.rs_white))
    backgroundTintList = ColorStateList.valueOf(context.color(bgColor))
}

fun ImageView.tint(@ColorRes res: Int) {
    imageTintList = ColorStateList.valueOf(context.color(res))
}

fun avatarColor(id: Int): Int {
    val colors = intArrayOf(
        R.color.rs_orange, R.color.rs_graphite, R.color.rs_blue,
        R.color.rs_green, R.color.rs_purple, R.color.rs_teal
    )
    return colors[Math.abs(id) % colors.size]
}

fun Context.confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(positive) { _, _ -> onConfirm() }
        .show()
}

fun Context.askText(
    title: String,
    message: String,
    hint: String,
    positive: String,
    numeric: Boolean = false,
    onText: (String) -> Unit
) {
    val dialogBinding = DialogInputBinding.inflate(LayoutInflater.from(this))
    dialogBinding.tvDialogMessage.text = message
    dialogBinding.tilDialogInput.hint = hint
    if (numeric) dialogBinding.etDialogInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(dialogBinding.root)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(positive, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val value = dialogBinding.etDialogInput.text?.toString()?.trim().orEmpty()
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

fun com.google.android.material.chip.ChipGroup.checkedTag(): Any? =
    findViewById<com.google.android.material.chip.Chip>(checkedChipId)?.tag

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
