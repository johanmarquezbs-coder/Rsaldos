package com.rsaldos.app.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.databinding.ActivitySplashBinding
import com.rsaldos.app.ui.common.Navigator

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

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

        handler.postDelayed({ goNext() }, SPLASH_MS)
    }

    private fun goNext() {
        val user = SessionManager(this).currentUser()
        if (user != null) {
            Navigator.openHome(this, user)
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_MS = 1800L
    }
}
