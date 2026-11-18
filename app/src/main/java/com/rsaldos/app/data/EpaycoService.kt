package com.rsaldos.app.data

import com.rsaldos.app.BuildConfig
import com.rsaldos.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Respuesta de ePayco sobre una transacción, obtenida del servicio de validación.
 *
 * `codResponse` sigue la tabla de ePayco: 1 aceptada, 2 rechazada, 3 pendiente, 4 fallida,
 * 6 reversada, 7 retenida, 8 iniciada, 9 expirada, 10 abandonada, 11 cancelada, 12 antifraude.
 */
data class EpaycoTransaction(
    val refPayco: String,
    val transactionId: String,
    val invoice: String,
    val amount: Double,
    val codResponse: Int,
    val responseText: String,
    val reason: String,
    val franchise: String,
    val bank: String
) {
    /** Resultado normalizado para la app. */
    val result: PaymentResult
        get() = when (codResponse) {
            1 -> PaymentResult.APPROVED
            3, 7, 8 -> PaymentResult.PENDING
            else -> PaymentResult.REJECTED
        }

    /** Texto del medio de pago para mostrar en el pedido. */
    val methodLabel: String
        get() = listOf("ePayco", franchise, bank).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Integración con la pasarela de pagos ePayco (Colombia).
 *
 * 1. [checkoutHtml] arma una página con el Checkout estándar de ePayco (checkout.js, versión
 *    clásica por llave pública) que se abre en un
 *    WebView: el comprador escribe los datos de su tarjeta, PSE o billetera directamente en el
 *    entorno certificado de ePayco, así la app nunca recibe datos de tarjeta.
 * 2. Al terminar, ePayco redirige a [RESPONSE_URL] con el parámetro `ref_payco`; el WebView
 *    intercepta esa URL.
 * 3. [validate] consulta el servicio de validación de ePayco con esa referencia y [verify]
 *    comprueba que la factura y el monto coincidan con el pedido antes de confirmarlo.
 */
object EpaycoService {
    /** URL de respuesta configurada en el checkout; no se carga, solo se intercepta. */
    const val RESPONSE_URL = "https://rsaldos.app/epayco/respuesta"
    private const val VALIDATION_URL = "https://secure.epayco.co/validation/v1/reference/"

    /** Indica si hay llave pública configurada en gradle.properties. */
    fun isConfigured(): Boolean = BuildConfig.EPAYCO_PUBLIC_KEY.isNotBlank()

    /** Indica si la pasarela está en modo pruebas. */
    val isTest: Boolean get() = BuildConfig.EPAYCO_TEST

    /** Genera el HTML que abre el Checkout de ePayco con los datos del pedido y del comprador. */
    fun checkoutHtml(order: Order, buyer: User): String {
        fun q(value: String) = JSONObject.quote(value)
        val description = order.lines.joinToString(", ") { "${it.quantity} x ${it.title}" }.take(240)
        return """
            <!DOCTYPE html>
            <html lang="es"><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body{font-family:sans-serif;margin:0;height:100vh;display:flex;flex-direction:column;
                   align-items:center;justify-content:center;color:#1E2227;background:#F5F6F8;text-align:center}
              .dot{width:44px;height:44px;border-radius:50%;border:4px solid #F26B1D;border-top-color:transparent;
                   animation:spin 1s linear infinite;margin-bottom:16px}
              @keyframes spin{to{transform:rotate(360deg)}}
              button{margin-top:16px;padding:12px 24px;border:0;border-radius:8px;background:#F26B1D;color:#fff;font-size:16px}
            </style></head>
            <body>
              <div class="dot"></div>
              <p id="msg">Abriendo la pasarela segura de ePayco…</p>
              <button id="retry" style="display:none" onclick="start(0)">Reintentar</button>
              <script src="https://checkout.epayco.co/checkout.js?version=v1"></script>
              <script>
                // El script de ePayco se inicializa de forma asíncrona: se espera hasta 15 s a que
                // ePayco.checkout esté disponible antes de abrir el checkout.
                function start(intentos){
                  intentos = intentos || 0;
                  if (!(window.ePayco && window.ePayco.checkout && window.ePayco.checkout.configure)) {
                    if (intentos < 75) { setTimeout(function(){ start(intentos + 1); }, 200); return; }
                    document.getElementById('msg').innerText = 'No se pudo cargar ePayco. Revise su conexión.';
                    document.getElementById('retry').style.display = 'inline-block';
                    return;
                  }
                  try {
                    var handler = ePayco.checkout.configure({ key: ${q(BuildConfig.EPAYCO_PUBLIC_KEY)}, test: ${BuildConfig.EPAYCO_TEST} });
                    handler.open({
                      name: ${q("Pedido " + order.id + " · R-Saldos")},
                      description: ${q(description)},
                      invoice: ${q(order.id)},
                      currency: "cop",
                      amount: ${q(order.total.toString())},
                      tax_base: "0", tax: "0", tax_ico: "0",
                      country: "co", lang: "es",
                      external: "true",
                      extra1: ${q(order.id)},
                      response: ${q(RESPONSE_URL)},
                      confirmation: ${q(RESPONSE_URL)},
                      name_billing: ${q(buyer.name)},
                      address_billing: ${q(order.address)},
                      mobilephone_billing: ${q(buyer.phone.filter { it.isDigit() })},
                      email_billing: ${q(buyer.email)}
                    });
                  } catch (e) {
                    document.getElementById('msg').innerText = 'No se pudo cargar ePayco. Revise su conexión.';
                    document.getElementById('retry').style.display = 'inline-block';
                  }
                }
                window.onload = function(){ start(0); };
              </script>
            </body></html>
        """.trimIndent()
    }

    /** Extrae la referencia `ref_payco` de la URL de respuesta, o null si no es la URL esperada. */
    fun refFromResponseUrl(url: String): String? {
        if (!url.startsWith(RESPONSE_URL)) return null
        val query = url.substringAfter('?', "")
        return query.split('&').map { it.split('=', limit = 2) }
            .firstOrNull { it.size == 2 && (it[0] == "ref_payco" || it[0] == "x_ref_payco") }
            ?.get(1)?.takeIf { it.isNotBlank() }
    }

    /** Consulta a ePayco el estado real de la transacción por su referencia. */
    suspend fun validate(refPayco: String): EpaycoTransaction = withContext(Dispatchers.IO) {
        val connection = URL(VALIDATION_URL + URLEncoder.encode(refPayco, "UTF-8")).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) throw AppException(R.string.error_payment_validation)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONObject("data") ?: throw AppException(R.string.error_payment_validation)
            EpaycoTransaction(
                refPayco = data.optString("x_ref_payco", refPayco),
                transactionId = data.optString("x_transaction_id"),
                invoice = data.optString("x_id_invoice"),
                amount = data.optDouble("x_amount", 0.0),
                codResponse = data.optInt("x_cod_response", 0),
                responseText = data.optString("x_response"),
                reason = data.optString("x_response_reason_text"),
                franchise = data.optString("x_franchise"),
                bank = data.optString("x_bank_name")
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Verifica que la transacción corresponda al pedido (misma factura y mismo monto).
     * Si no coincide, se trata como rechazada para no confirmar un pago ajeno o alterado.
     */
    fun verify(order: Order, tx: EpaycoTransaction): EpaycoTransaction {
        val sameInvoice = tx.invoice.isBlank() || tx.invoice == order.id
        val sameAmount = abs(tx.amount - order.total.toDouble()) < 1.0
        return if (sameInvoice && sameAmount) tx
        else tx.copy(codResponse = 2, responseText = "Validación fallida", reason = "La factura o el monto no coinciden con el pedido")
    }
}
