package com.rsaldos.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Almacenamiento de las fotos de producto en Cloud Firestore (plan gratuito Spark).
 *
 * Firebase Storage exige el plan de pago Blaze, por eso cada foto se guarda como un documento
 * de la colección `productPhotos`: la imagen se reduce a máximo [MAX_SIZE] píxeles por lado, se
 * corrige su orientación EXIF, se comprime en JPEG por debajo de [MAX_BYTES] (el límite de un
 * documento de Firestore es 1 MiB) y se guarda en Base64. En el producto solo se guarda la
 * referencia `firestore://productPhotos/{id}`, que [FirestorePhotoFetcher] resuelve al mostrarla.
 */
object StorageService {
    private const val TAG = "StorageService"
    const val COLLECTION = "productPhotos"
    const val SCHEME = "firestore"
    private const val MAX_SIZE = 960
    private const val MAX_BYTES = 450_000

    /** Comprime y guarda una foto; devuelve la referencia que se guarda en el producto. */
    suspend fun uploadProductPhoto(context: Context, productId: String, uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { compress(context, uri) }
        val id = "${productId}_${UUID.randomUUID().toString().take(8)}"
        val data = mapOf(
            "productId" to productId,
            "mimeType" to "image/jpeg",
            "data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection(COLLECTION).document(id).set(data).await()
        return "$SCHEME://$COLLECTION/$id"
    }

    /** Borra (sin bloquear la interfaz) las fotos de un producto eliminado. */
    fun deleteProductPhotos(product: Product) {
        product.photoUrls.forEach { deletePhoto(it) }
    }

    /** Borra una foto a partir de su referencia `firestore://productPhotos/{id}`. */
    fun deletePhoto(url: String) {
        val id = docIdOf(url) ?: return
        FirebaseFirestore.getInstance().collection(COLLECTION).document(id).delete()
            .addOnFailureListener { Log.w(TAG, "No se pudo borrar $url", it) }
    }

    /** Extrae el id del documento de una referencia de foto, o null si no es de Firestore. */
    fun docIdOf(url: String): String? =
        if (url.startsWith("$SCHEME://$COLLECTION/")) url.substringAfterLast('/').takeIf { it.isNotBlank() } else null

    /** Descarga y decodifica los bytes JPEG de una foto guardada en Firestore. */
    suspend fun downloadPhoto(docId: String): ByteArray {
        val snap = FirebaseFirestore.getInstance().collection(COLLECTION).document(docId).get().await()
        val encoded = snap.getString("data") ?: throw AppException(com.rsaldos.app.R.string.error_photo_read)
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /** Redimensiona, rota según EXIF y comprime en JPEG bajando la calidad hasta caber en [MAX_BYTES]. */
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
        var quality = 80
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 30)
        return bytes
    }
}
