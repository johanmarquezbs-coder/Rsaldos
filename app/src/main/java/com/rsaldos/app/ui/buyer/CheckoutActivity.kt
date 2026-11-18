package com.rsaldos.app.ui.buyer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rsaldos.app.R
import com.rsaldos.app.data.EpaycoService
import com.rsaldos.app.data.Format
import com.rsaldos.app.data.LocationService
import com.rsaldos.app.data.Repository
import com.rsaldos.app.data.SessionManager
import com.rsaldos.app.data.User
import com.rsaldos.app.data.userMessage
import com.rsaldos.app.databinding.ActivityCheckoutBinding
import com.rsaldos.app.ui.common.color
import com.rsaldos.app.ui.common.progressDialog
import com.rsaldos.app.ui.common.toast
import com.rsaldos.app.ui.common.visible
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Confirmación de la compra: dirección de entrega, ubicación GPS, resumen y pago.
 *
 * 1. "Usar mi ubicación" pide el permiso, obtiene las coordenadas con el Fused Location
 *    Provider de Google y completa la dirección y la ciudad con el Geocoder.
 * 2. "Pagar" crea el pedido en Firestore (pago pendiente) con la dirección y las coordenadas,
 *    y abre [EpaycoActivity] con el checkout de ePayco.
 * 3. Al volver, [PaymentResultActivity] muestra el resultado ya validado con ePayco.
 */
class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var user: User
    private lateinit var location: LocationService
    private var latitude: Double? = null
    private var longitude: Double? = null

    /** Permisos de ubicación precisa y aproximada (el usuario puede conceder solo la aproximada). */
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) captureLocation()
        else showLocationStatus(getString(R.string.checkout_location_denied), R.color.rs_red)
    }

    /** Resultado de la pasarela: si el pago no fue rechazado, se cierra el checkout. */
    private val epayco = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val current = SessionManager(this).currentUser()
        if (current == null || Repository.cart.isEmpty()) {
            toast(getString(R.string.cart_empty_title))
            finish()
            return
        }
        user = current
        location = LocationService(this)

        binding.appBar.root.title = getString(R.string.checkout_title)
        binding.appBar.root.setNavigationOnClickListener { finish() }

        binding.etAddress.setText(user.address)
        binding.etCity.setText(user.city)
        binding.btnUseLocation.setOnClickListener { requestLocation() }
        binding.btnOpenMap.setOnClickListener { openMap() }

        binding.tvEpaycoMode.text = getString(
            if (EpaycoService.isTest) R.string.checkout_epayco_test else R.string.checkout_epayco_live
        )
        binding.rgDelivery.setOnCheckedChangeListener { _, _ -> updateSummary() }
        binding.btnPay.setOnClickListener { pay() }
        updateSummary()
    }

    /** Pide el permiso o, si ya existe, captura la ubicación directamente. */
    private fun requestLocation() {
        if (location.hasPermission()) captureLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /** Obtiene las coordenadas y la dirección y llena el formulario de entrega. */
    private fun captureLocation() {
        showLocationStatus(getString(R.string.checkout_location_searching), R.color.rs_gray_text)
        binding.btnUseLocation.isEnabled = false
        lifecycleScope.launch {
            try {
                val fix = location.capture()
                if (fix == null) {
                    showLocationStatus(getString(R.string.checkout_location_unavailable), R.color.rs_red)
                    return@launch
                }
                latitude = fix.latitude
                longitude = fix.longitude
                fix.street?.let { binding.etAddress.setText(it) }
                fix.city?.let { binding.etCity.setText(it) }
                if (!user.locationConsent) {
                    user.locationConsent = true
                    Repository.updateProfile(user, "Autorizó la ubicación para entregas")
                }
                showLocationStatus(
                    getString(R.string.checkout_location_ok_coords, fix.city ?: "—",
                        String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude)),
                    R.color.rs_green
                )
                binding.btnOpenMap.visible(true)
            } finally {
                binding.btnUseLocation.isEnabled = true
            }
        }
    }

    /** Muestra el estado de la captura de ubicación. */
    private fun showLocationStatus(text: String, colorRes: Int) {
        binding.tvLocationStatus.text = text
        binding.tvLocationStatus.setTextColor(color(colorRes))
    }

    /** Abre las coordenadas capturadas en Google Maps (o en la app de mapas disponible). */
    private fun openMap() {
        val lat = latitude ?: return
        val lng = longitude ?: return
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(getString(R.string.checkout_map_label))})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")))
        }
    }

    /** Costo de envío según el tipo de entrega. */
    private fun shipping(): Long = if (binding.rbPickup.isChecked) 0L else Repository.SHIPPING_COST

    /** Total a pagar. */
    private fun total(): Long = Repository.cartTotal() + shipping()

    /** Muestra las líneas del carrito, el envío y el total. */
    private fun updateSummary() {
        binding.tvItems.text = Repository.cart.joinToString("\n") {
            "${it.quantity} × ${it.product.title} (${it.product.grade.label}) — ${Format.money(it.subtotal)}"
        }
        binding.tvSummary.text = getString(
            R.string.checkout_summary,
            Format.money(Repository.cartTotal()),
            if (shipping() == 0L) getString(R.string.free) else Format.money(shipping()),
            Format.money(total())
        )
        binding.btnPay.text = getString(R.string.checkout_pay, Format.money(total()))
    }

    /** Valida la entrega, crea el pedido pendiente en Firestore y abre la pasarela ePayco. */
    private fun pay() {
        binding.tilAddress.error = null
        binding.tilCity.error = null
        val address = binding.etAddress.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        if (address.length < 6) { binding.tilAddress.error = getString(R.string.error_address); return }
        if (city.isEmpty()) { binding.tilCity.error = getString(R.string.error_required); return }
        if (!binding.cbTerms.isChecked) { toast(getString(R.string.error_terms)); return }
        if (!EpaycoService.isConfigured()) { toast(getString(R.string.error_epayco_key)); return }
        val outOfStock = Repository.cart.firstOrNull { it.quantity > it.product.stock }
        if (outOfStock != null) { toast(getString(R.string.cart_no_stock)); return }

        binding.btnPay.isEnabled = false
        val progress = progressDialog(getString(R.string.checkout_processing))
        lifecycleScope.launch {
            try {
                val order = Repository.createPendingOrder(user, "$address, $city", latitude, longitude, shipping())
                if (user.address.isBlank() || user.city != city) {
                    user.address = address
                    user.city = city
                    Repository.updateProfile(user, "Dirección de entrega")
                }
                epayco.launch(Intent(this@CheckoutActivity, EpaycoActivity::class.java)
                    .putExtra(EpaycoActivity.EXTRA_ORDER, order.id))
            } catch (e: Exception) {
                toast(getString(e.userMessage()))
            } finally {
                progress.dismiss()
                binding.btnPay.isEnabled = true
            }
        }
    }
}
