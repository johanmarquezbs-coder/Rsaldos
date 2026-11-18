package com.rsaldos.app.data

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer

/**
 * Extensión de Coil que muestra las fotos guardadas en Firestore.
 *
 * Cuando una imagen tiene una referencia `firestore://productPhotos/{id}`, este fetcher lee el
 * documento, decodifica el Base64 y entrega los bytes JPEG a Coil, que los decodifica y los
 * guarda en su caché de memoria como cualquier otra imagen.
 */
class FirestorePhotoFetcher(private val docId: String, private val options: Options) : Fetcher {

    /** Descarga la foto desde Firestore y la entrega a Coil. */
    override suspend fun fetch(): FetchResult {
        val bytes = StorageService.downloadPhoto(docId)
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK
        )
    }

    /** Crea el fetcher solo para las URI con esquema `firestore`. */
    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != StorageService.SCHEME) return null
            val id = StorageService.docIdOf(data.toString()) ?: return null
            return FirestorePhotoFetcher(id, options)
        }
    }
}
