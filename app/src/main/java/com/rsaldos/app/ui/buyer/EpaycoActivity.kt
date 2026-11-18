package com.rsaldos.app.ui.buyer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.R
import com.rsaldos.app.data.EpaycoService
import com.rsaldos.app.data.Order
import com.rsaldos.app.data.OrderStatus
import com.rsaldos.app.data.PaymentResult
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.ActivityEpaycoBinding
import com.rsaldos.app.ui.common.confirm
import com.rsaldos.app.ui.common.progressDialog
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos ePayco dentro de un WebView.
 *
 * Carga el Checkout de ePayco con los datos del pedido. El comprador paga con tarjeta, PSE,
 * Nequi u otros medios en el entorno certificado de ePayco. Cuando ePayco redirige a la URL de
 * respuesta con `ref_payco`, la actividad intercepta la navegación, valida la transacción con
 * el servicio de ePayco, comprueba factura y monto, actualiza el pedido en Firestore y abre
 * [PaymentResultActivity].
 */
class EpaycoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEpaycoBinding
    private lateinit var order: Order
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpaycoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.root.title = getString(R.string.epayco_title)
        binding.appBar.root.setNavigationOnClickListener { askCancel() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else askCancel()
            }
        })

        val buyer = SessionManager(this).currentUser()
        val orderId = intent.getStringExtra(EXTRA_ORDER).orEmpty()
        if (buyer == null || orderId.isEmpty()) { finish(); return }

        lifecycleScope.launch {
            val found = Repository.order(orderId) ?: try { Repository.fetchOrder(orderId) } catch (e: Exception) { null }
            if (found == null) {
                toast(getString(R.string.product_not_found))
                finish()
                return@launch
            }
            order = found
            setupWebView()
            binding.webView.loadDataWithBaseURL(BASE_URL, EpaycoService.checkoutHtml(order, buyer), "text/html", "UTF-8", null)
        }
    }

    /** Configura el WebView: JavaScript, almacenamiento, cookies de terceros e intercepción de URL. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val web = binding.webView
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.visible(newProgress < 100)
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                intercept(request.url.toString())

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url.startsWith(EpaycoService.RESPONSE_URL)) {
                    view.stopLoading()
                    intercept(url)
                } else super.onPageStarted(view, url, favicon)
            }
        }
    }

    /**
     * Decide qué hacer con cada navegación: la URL de respuesta dispara la validación; los
     * esquemas externos (apps de bancos, intent://) se abren fuera; el resto sigue en el WebView.
     */
    private fun intercept(url: String): Boolean {
        if (url.startsWith(EpaycoService.RESPONSE_URL)) {
            val ref = EpaycoService.refFromResponseUrl(url)
            if (ref != null) validate(ref) else finishWith(PaymentResult.PENDING, getString(R.string.epayco_no_reference))
            return true
        }
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("about:") && !url.startsWith("data:")) {
            try {
                startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME))
            } catch (e: Exception) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
            }
            return true
        }
        return false
    }

    /** Valida con ePayco la referencia recibida y registra el resultado en Firestore. */
    private fun validate(refPayco: String) {
        if (handled) return
        handled = true
        val progress = progressDialog(getString(R.string.epayco_validating))
        lifecycleScope.launch {
            try {
                val tx = EpaycoService.verify(order, EpaycoService.validate(refPayco))
                Repository.confirmPayment(order.id, tx)
                progress.dismiss()
                finishWith(tx.result, tx.reason.ifBlank { tx.responseText })
            } catch (e: Exception) {
                progress.dismiss()
                Repository.log(order.buyerName, "Validación de pago fallida", "${order.id} · ref $refPayco", com.rsaldos.app.data.AuditType.PAYMENTS)
                finishWith(PaymentResult.PENDING, getString(e.userMessage()))
            }
        }
    }

    /** Abre la pantalla de resultado y cierra la pasarela. */
    private fun finishWith(result: PaymentResult, reason: String) {
        startActivity(Intent(this, PaymentResultActivity::class.java)
            .putExtra(PaymentResultActivity.EXTRA_ORDER, order.id)
            .putExtra(PaymentResultActivity.EXTRA_RESULT, result.name)
            .putExtra(PaymentResultActivity.EXTRA_REASON, reason))
        setResult(if (result == PaymentResult.REJECTED) RESULT_CANCELED else RESULT_OK)
        finish()
    }

    /** Pregunta si se abandona el pago; el pedido queda cancelado. */
    private fun askCancel() {
        if (!::order.isInitialized) { finish(); return }
        confirm(getString(R.string.epayco_cancel_title), getString(R.string.epayco_cancel_message), getString(R.string.epayco_cancel_confirm)) {
            SessionManager(this).currentUser()?.let { Repository.setOrderStatus(it, order, OrderStatus.CANCELLED) }
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ORDER = "order_id"
        private const val BASE_URL = "https://rsaldos.app/"
    }
}
