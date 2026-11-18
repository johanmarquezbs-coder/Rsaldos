package com.rsaldos.app

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Clase Application de R-Saldos.
 *
 * Inicializa Firebase al arrancar el proceso. Si el proyecto aún no tiene
 * app/google-services.json, `initializeApp` devuelve null y la pantalla de inicio
 * muestra cómo configurarlo en lugar de cerrarse con un error.
 */
class RSaldosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
