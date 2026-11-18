package com.rsaldos.app.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rsaldos.app.R
import com.rsaldos.app.data.AuthService
import com.rsaldos.app.data.Repository
import com.rsaldos.app.databinding.ActivitySplashBinding
import com.rsaldos.app.ui.common.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de bienvenida con la marca animada.
 *
 * Mientras corre la animación verifica que Firebase esté configurado y restaura la sesión
 * guardada por Firebase Authentication: si hay una cuenta activa abre su pantalla de rol;
 * si no, abre el inicio de sesión.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.6f
        binding.ivLogo.scaleY = 0.6f
        binding.ivLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        binding.tvBrand.alpha = 0f
        binding.tvBrand.animate().alpha(1f).setStartDelay(300).setDuration(600).start()

        if (!Repository.isFirebaseReady(this)) {
            showMissingFirebase()
            return
        }
        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val user = AuthService.restoreSession()
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < SPLASH_MS) delay(SPLASH_MS - elapsed)
            if (user != null) {
                Repository.start(user)
                Navigator.openHome(this@SplashActivity, user)
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }

    /** Explica cómo conectar el proyecto a Firebase cuando falta google-services.json. */
    private fun showMissingFirebase() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.firebase_missing_title)
            .setMessage(R.string.firebase_missing_message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_close) { _, _ -> finish() }
            .show()
    }

    companion object {
        private const val SPLASH_MS = 1500L
    }
}
