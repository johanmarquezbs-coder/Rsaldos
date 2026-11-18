package com.rsaldos.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.FirebaseApp
import com.rsaldos.app.data.FirestorePhotoFetcher

/**
 * Clase Application de R-Saldos.
 *
 * Inicializa Firebase al arrancar el proceso. Si el proyecto aún no tiene
 * app/google-services.json, `initializeApp` devuelve null y la pantalla de inicio
 * muestra cómo configurarlo en lugar de cerrarse con un error.
 * También configura el cargador de imágenes Coil para que entienda las fotos de producto
 * guardadas en Firestore.
 */
class RSaldosApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }

    /** Cargador de imágenes de la app con soporte para referencias `firestore://`. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(FirestorePhotoFetcher.Factory()) }
        .crossfade(true)
        .build()
}
