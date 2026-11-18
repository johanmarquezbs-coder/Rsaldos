package com.rsaldos.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Subida y borrado de las fotos de producto en Firebase Storage.
 *
 * Cada imagen (de la cámara o de la galería) se reduce a máximo [MAX_SIZE] píxeles por lado,
 * se corrige su orientación según los datos EXIF y se comprime en JPEG antes de subirla a
 * `products/{productId}/{uuid}.jpg`. En Firestore solo se guarda la URL de descarga.
 */
object StorageService {
    private const val TAG = "StorageService"
    private const val MAX_SIZE = 1280
    private const val QUALITY = 82

    /** Sube una foto y devuelve su URL pública de descarga. */
    suspend fun uploadProductPhoto(context: Context, productId: String, uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { compress(context, uri) }
        val ref = FirebaseStorage.getInstance().reference
            .child("products/$productId/${UUID.randomUUID()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(bytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    /** Borra (sin bloquear la interfaz) las fotos de un producto eliminado. */
    fun deleteProductPhotos(product: Product) {
        product.photoUrls.forEach { deletePhoto(it) }
    }

    /** Borra una foto a partir de su URL de descarga. */
    fun deletePhoto(url: String) {
        try {
            FirebaseStorage.getInstance().getReferenceFromUrl(url).delete()
                .addOnFailureListener { Log.w(TAG, "No se pudo borrar $url", it) }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "URL de Storage inválida: $url", e)
        }
    }

    /** Redimensiona, rota según EXIF y comprime la imagen en JPEG. */
    private fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE || bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw AppException(com.rsaldos.app.R.string.error_photo_read)

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = MAX_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (rotation != 0f || scale < 1f) {
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }
}
