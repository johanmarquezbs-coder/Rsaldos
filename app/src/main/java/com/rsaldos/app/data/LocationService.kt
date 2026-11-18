package com.rsaldos.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Geolocalización con las APIs de Google Play Services.
 *
 * Usa el Fused Location Provider para obtener la ubicación actual del dispositivo y el
 * Geocoder de Android (servicio de geocodificación de Google) para convertir las coordenadas
 * en una dirección legible que se asocia a la entrega del pedido.
 */
class LocationService(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Resultado de la captura: coordenadas y, si se pudo resolver, la dirección. */
    data class Fix(val latitude: Double, val longitude: Double, val street: String?, val city: String?)

    /** Indica si el usuario ya concedió algún permiso de ubicación. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Obtiene la ubicación actual con alta precisión; si no está disponible usa la última conocida.
     * Debe llamarse solo después de verificar [hasPermission].
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val token = CancellationTokenSource()
        return try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
                ?: client.lastLocation.await()
        } finally {
            token.cancel()
        }
    }

    /** Captura la ubicación y la traduce a dirección y ciudad con el Geocoder. */
    suspend fun capture(): Fix? {
        val location = currentLocation() ?: return null
        val address = reverseGeocode(location.latitude, location.longitude)
        val street = address?.let { a -> formatStreet(a) }
        val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        return Fix(location.latitude, location.longitude, street, city)
    }

    /**
     * Arma la dirección al estilo colombiano ("Calle 93A # 13-24"). Si el Geocoder ya trae el
     * número con "#", no se repite; si falta la calle, usa la primera línea de la dirección.
     */
    private fun formatStreet(a: Address): String? {
        val street = a.thoroughfare?.trim()?.removeSuffix("#")?.trim().orEmpty()
        val number = a.subThoroughfare?.trim()?.removePrefix("#")?.trim().orEmpty()
        return when {
            street.isNotEmpty() && number.isNotEmpty() -> "$street # $number"
            street.isNotEmpty() -> street
            else -> a.getAddressLine(0)?.substringBefore(",")
        }
    }

    /** Geocodificación inversa: coordenadas → dirección (en un hilo de entrada/salida). */
    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            Geocoder(context, Locale("es", "CO")).getFromLocation(latitude, longitude, 1)?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
