package com.rsaldos.app.ui.seller

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.R
import com.rsaldos.app.data.Category
import com.rsaldos.app.data.Condition
import com.rsaldos.app.data.Grade
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.databinding.ActivityProductFormBinding
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible

class ProductFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductFormBinding
    private lateinit var actor: User
    private var editing: Product? = null
    private var photoCount = 0

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) addThumbnail(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = SessionManager(this).currentUser()
        if (current == null) { finish(); return }
        actor = current

        val id = intent.getIntExtra(EXTRA_ID, -1)
        editing = if (id >= 0) Repository.product(id) else null

        binding.appBar.root.title = getString(if (editing == null) R.string.form_new_title else R.string.form_edit_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.actCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Category.values().map { it.label }))
        binding.actCondition.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Condition.values().map { it.label }))
        binding.chipGrades.setOnCheckedStateChangeListener { _, _ -> updateGradeHelp() }

        binding.btnTakePhoto.setOnClickListener {
            try {
                takePhoto.launch(null)
            } catch (e: ActivityNotFoundException) {
                toast(getString(R.string.form_no_camera))
            }
        }
        binding.tilSerial.setEndIconOnClickListener {
            val code = "SN" + (10_000_000..99_999_999).random()
            binding.etSerial.setText(code)
            toast(getString(R.string.form_serial_scanned))
        }

        if (actor.role == Role.ADMIN) binding.tvReviewNote.text = getString(R.string.form_admin_note)
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnDelete.visible(editing != null)

        fill()
        updateGradeHelp()
    }

    private fun fill() {
        val p = editing
        if (p == null) {
            binding.actCategory.setText(Category.PHONES.label, false)
            binding.actCondition.setText(Condition.REFURBISHED.label, false)
            binding.etWarranty.setText("6")
            binding.etStock.setText("1")
            return
        }
        binding.etTitle.setText(p.title)
        binding.etBrand.setText(p.brand)
        binding.actCategory.setText(p.category.label, false)
        binding.actCondition.setText(p.condition.label, false)
        binding.chipGrades.check(
            when (p.grade) {
                Grade.A -> R.id.chipA
                Grade.B -> R.id.chipB
                Grade.C -> R.id.chipC
            }
        )
        binding.etPrice.setText(p.price.toString())
        binding.etOldPrice.setText(if (p.oldPrice > 0) p.oldPrice.toString() else "")
        binding.etStock.setText(p.stock.toString())
        binding.etWarranty.setText(p.warrantyMonths.toString())
        binding.etSerial.setText(p.serial)
        binding.etTests.setText(p.tests)
        binding.etDefects.setText(p.defects)
        binding.etDescription.setText(p.description)
        photoCount = p.photos
        repeat(p.photos.coerceAtMost(4)) { addPlaceholder(p.category.icon) }
        if (p.status == ProductStatus.REJECTED && p.rejectReason.isNotBlank()) {
            binding.tvRejectReason.visible(true)
            binding.tvRejectReason.text = getString(R.string.form_rejected, p.rejectReason)
        }
    }

    private fun selectedGrade(): Grade = when (binding.chipGrades.checkedChipId) {
        R.id.chipB -> Grade.B
        R.id.chipC -> Grade.C
        else -> Grade.A
    }

    private fun updateGradeHelp() {
        binding.tvGradeHelp.text = selectedGrade().detail
    }

    private fun thumbnailView(): ImageView {
        val size = resources.getDimensionPixelSize(R.dimen.avatar_size) * 2 + 8
        val view = ImageView(this)
        val params = LinearLayout.LayoutParams(size, size)
        params.marginStart = resources.getDimensionPixelSize(R.dimen.space_s)
        view.layoutParams = params
        view.setBackgroundResource(R.drawable.bg_image_box)
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.clipToOutline = true
        return view
    }

    private fun addThumbnail(bitmap: Bitmap) {
        val view = thumbnailView()
        view.setImageBitmap(bitmap)
        binding.containerPhotos.addView(view)
        photoCount++
        toast(getString(R.string.form_photo_added, photoCount))
    }

    private fun addPlaceholder(icon: Int) {
        val view = thumbnailView()
        view.setImageResource(icon)
        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = resources.getDimensionPixelSize(R.dimen.space_l)
        view.setPadding(pad, pad, pad, pad)
        binding.containerPhotos.addView(view)
    }

    private fun text(view: com.google.android.material.textfield.TextInputEditText) = view.text?.toString()?.trim().orEmpty()

    private fun save() {
        val fields = listOf(binding.tilTitle, binding.tilBrand, binding.tilCategory, binding.tilCondition, binding.tilPrice,
            binding.tilOldPrice, binding.tilStock, binding.tilWarranty, binding.tilSerial, binding.tilTests, binding.tilDefects)
        fields.forEach { it.error = null }

        val title = text(binding.etTitle)
        val brand = text(binding.etBrand)
        val category = Category.values().firstOrNull { it.label == binding.actCategory.text.toString() }
        val condition = Condition.values().firstOrNull { it.label == binding.actCondition.text.toString() }
        val price = text(binding.etPrice).toLongOrNull() ?: 0L
        val oldPrice = text(binding.etOldPrice).toLongOrNull() ?: 0L
        val stock = text(binding.etStock).toIntOrNull() ?: -1
        val warranty = text(binding.etWarranty).toIntOrNull() ?: -1
        val serial = text(binding.etSerial).uppercase()

        var ok = true
        if (title.length < 5) { binding.tilTitle.error = getString(R.string.error_title); ok = false }
        if (brand.isEmpty()) { binding.tilBrand.error = getString(R.string.error_required); ok = false }
        if (category == null) { binding.tilCategory.error = getString(R.string.error_required); ok = false }
        if (condition == null) { binding.tilCondition.error = getString(R.string.error_required); ok = false }
        if (price < 10_000) { binding.tilPrice.error = getString(R.string.error_price); ok = false }
        if (oldPrice != 0L && oldPrice < price) { binding.tilOldPrice.error = getString(R.string.error_old_price); ok = false }
        if (stock < 1) { binding.tilStock.error = getString(R.string.error_stock); ok = false }
        if (warranty !in 0..24) { binding.tilWarranty.error = getString(R.string.error_warranty); ok = false }
        if (serial.length < 6) { binding.tilSerial.error = getString(R.string.error_serial); ok = false }
        if (text(binding.etTests).isEmpty()) { binding.tilTests.error = getString(R.string.error_tests); ok = false }
        if (text(binding.etDefects).isEmpty()) { binding.tilDefects.error = getString(R.string.error_defects); ok = false }
        if (photoCount == 0 && editing == null) { toast(getString(R.string.error_photos)); ok = false }
        if (!ok || category == null || condition == null) return

        val base = editing
        val product = Product(
            id = base?.id ?: -1,
            title = title,
            brand = brand,
            category = category,
            condition = condition,
            grade = selectedGrade(),
            price = price,
            oldPrice = oldPrice,
            stock = stock,
            serial = serial,
            warrantyMonths = warranty,
            defects = text(binding.etDefects),
            tests = text(binding.etTests),
            description = text(binding.etDescription),
            sellerId = base?.sellerId ?: actor.id,
            status = base?.status ?: ProductStatus.REVIEW,
            photos = photoCount.coerceAtLeast(1),
            rating = base?.rating ?: 0.0
        )
        val saved = Repository.saveProduct(actor, product)
        toast(getString(R.string.form_saved, saved.status.label))
        finish()
    }

    private fun delete() {
        val p = editing ?: return
        confirm(getString(R.string.product_delete_title), getString(R.string.product_delete_message, p.title), getString(R.string.action_delete)) {
            Repository.deleteProduct(actor, p)
            toast(getString(R.string.product_deleted))
            finish()
        }
    }

    companion object {
        const val EXTRA_ID = "product_id"
    }
}
