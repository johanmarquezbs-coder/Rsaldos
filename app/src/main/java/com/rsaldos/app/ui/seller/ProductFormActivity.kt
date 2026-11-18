package com.rsaldos.app.ui.seller

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.rsaldos.app.R
import com.rsaldos.app.data.Category
import com.rsaldos.app.data.Condition
import com.rsaldos.app.data.Grade
import com.rsaldos.app.data.Product
import com.rsaldos.app.data.ProductStatus
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.Role
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.StorageService
import com.rsaldos.app.data.User
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.ActivityProductFormBinding
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.progressDialog
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import kotlinx.coroutines.launch
import java.io.File

/**
 * Formulario de producto (Create, Update y Delete del CRUD de productos) para el vendedor y
 * el administrador.
 *
 * Las fotos se toman con la cámara (a resolución completa, mediante un FileProvider) o se
 * eligen de la galería con el selector de fotos del sistema. Al guardar, las fotos nuevas se
 * suben a Firebase Storage y el producto se escribe en Firestore con sus URL. Mantener
 * presionada una miniatura la quita del producto.
 */
class ProductFormActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProductFormBinding
    private lateinit var actor: User
    private var editing: Product? = null
    private lateinit var productId: String

    /** Foto del formulario: ya subida a Storage (remota) o tomada/elegida y pendiente de subir (local). */
    private sealed class Photo {
        data class Remote(val url: String) : Photo()
        data class Local(val uri: Uri) : Photo()
    }

    private val photos = mutableListOf<Photo>()
    private val removedUrls = mutableListOf<String>()
    private var pendingCameraUri: Uri? = null

    /** Resultado de la cámara: la foto queda en el archivo del FileProvider. */
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) addPhoto(Photo.Local(uri))
        pendingCameraUri = null
    }

    /** Resultado del selector de fotos de la galería (hasta [MAX_PHOTOS]). */
    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        uris.take(MAX_PHOTOS - photos.size).forEach { addPhoto(Photo.Local(it)) }
        if (uris.size > MAX_PHOTOS - photos.size) toast(getString(R.string.form_max_photos, MAX_PHOTOS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = SessionManager(this).currentUser()
        if (current == null) { finish(); return }
        actor = current

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        editing = if (id.isNotEmpty()) Repository.product(id) else null
        productId = editing?.id ?: savedInstanceState?.getString(STATE_ID) ?: Repository.newProductId()
        savedInstanceState?.getString(STATE_CAMERA)?.let { pendingCameraUri = Uri.parse(it) }

        binding.appBar.root.title = getString(if (editing == null) R.string.form_new_title else R.string.form_edit_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.actCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Category.values().map { it.label }))
        binding.actCondition.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Condition.values().map { it.label }))
        binding.chipGrades.setOnCheckedStateChangeListener { _, _ -> updateGradeHelp() }

        binding.btnTakePhoto.setOnClickListener { openCamera() }
        binding.btnPickGallery.setOnClickListener {
            if (photos.size >= MAX_PHOTOS) toast(getString(R.string.form_max_photos, MAX_PHOTOS))
            else pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.tilSerial.setEndIconOnClickListener {
            binding.etSerial.setText("SN" + (10_000_000..99_999_999).random())
            toast(getString(R.string.form_serial_scanned))
        }

        if (actor.role == Role.ADMIN) binding.tvReviewNote.text = getString(R.string.form_admin_note)
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnDelete.visible(editing != null)

        fill()
        updateGradeHelp()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, productId)
        pendingCameraUri?.let { outState.putString(STATE_CAMERA, it.toString()) }
    }

    /** Crea un archivo temporal en caché y abre la cámara para que guarde ahí la foto. */
    private fun openCamera() {
        if (photos.size >= MAX_PHOTOS) {
            toast(getString(R.string.form_max_photos, MAX_PHOTOS))
            return
        }
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.form_no_camera))
        }
    }

    /** Carga en el formulario los datos del producto que se edita. */
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
        p.photoUrls.forEach { addPhoto(Photo.Remote(it), announce = false) }
        if (p.status == ProductStatus.REJECTED && p.rejectReason.isNotBlank()) {
            binding.tvRejectReason.visible(true)
            binding.tvRejectReason.text = getString(R.string.form_rejected, p.rejectReason)
        }
    }

    /** Grado estético elegido en los chips. */
    private fun selectedGrade(): Grade = when (binding.chipGrades.checkedChipId) {
        R.id.chipB -> Grade.B
        R.id.chipC -> Grade.C
        else -> Grade.A
    }

    /** Muestra la descripción del grado elegido. */
    private fun updateGradeHelp() {
        binding.tvGradeHelp.text = selectedGrade().detail
    }

    /** Agrega una miniatura de la foto; mantenerla presionada la elimina. */
    private fun addPhoto(photo: Photo, announce: Boolean = true) {
        photos += photo
        val size = resources.getDimensionPixelSize(R.dimen.avatar_size) * 2 + 8
        val view = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.space_s)
            }
            setBackgroundResource(R.drawable.bg_image_box)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            contentDescription = getString(R.string.form_photo_description)
        }
        when (photo) {
            is Photo.Remote -> view.load(photo.url) { crossfade(true) }
            is Photo.Local -> view.load(photo.uri) { crossfade(true) }
        }
        view.setOnLongClickListener {
            confirm(getString(R.string.form_remove_photo_title), getString(R.string.form_remove_photo_message), getString(R.string.action_delete)) {
                val index = photos.indexOf(photo)
                if (index >= 0) {
                    photos.removeAt(index)
                    if (photo is Photo.Remote) removedUrls += photo.url
                    binding.containerPhotos.removeView(view)
                }
            }
            true
        }
        binding.containerPhotos.addView(view)
        if (announce) toast(getString(R.string.form_photo_added, photos.size))
    }

    /** Texto recortado de un campo. */
    private fun text(view: com.google.android.material.textfield.TextInputEditText) = view.text?.toString()?.trim().orEmpty()

    /**
     * Valida el formulario, sube a Storage las fotos nuevas, guarda el producto en Firestore y
     * borra de Storage las fotos que el usuario quitó.
     */
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
        if (photos.isEmpty()) { toast(getString(R.string.error_photos)); ok = false }
        if (!ok || category == null || condition == null) return

        val base = editing
        binding.btnSave.isEnabled = false
        val progress = progressDialog(getString(R.string.form_uploading))
        lifecycleScope.launch {
            try {
                val urls = photos.map { photo ->
                    when (photo) {
                        is Photo.Remote -> photo.url
                        is Photo.Local -> StorageService.uploadProductPhoto(this@ProductFormActivity, productId, photo.uri)
                    }
                }
                val product = Product(
                    id = productId,
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
                    sellerName = base?.sellerName ?: actor.storeName.ifBlank { actor.name },
                    photoUrls = urls,
                    rating = base?.rating ?: 0.0
                )
                val saved = Repository.saveProduct(actor, product)
                removedUrls.forEach { StorageService.deletePhoto(it) }
                toast(getString(R.string.form_saved, saved.status.label))
                finish()
            } catch (e: Exception) {
                toast(getString(e.userMessage()))
            } finally {
                progress.dismiss()
                binding.btnSave.isEnabled = true
            }
        }
    }

    /** Elimina el producto (Delete del CRUD) y sus fotos. */
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
        private const val STATE_ID = "state_product_id"
        private const val STATE_CAMERA = "state_camera_uri"
        private const val MAX_PHOTOS = 8
    }
}
