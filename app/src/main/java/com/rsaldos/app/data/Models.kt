package com.rsaldos.app.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.rsaldos.app.R

/**
 * Modelos de dominio de R-Saldos.
 *
 * Cada entidad se guarda en Cloud Firestore como un documento; los identificadores (`id`)
 * son los ids de documento (String). La conversión entre estos modelos y los mapas que
 * entiende Firestore vive en [Mappers.kt].
 */

/** Rol del usuario. Define el menú, las pantallas y los permisos dentro de la app. */
enum class Role(val label: String) {
    BUYER("Comprador"),
    SELLER("Vendedor"),
    ADMIN("Administrador")
}

/** Estado de la cuenta. Solo las cuentas ACTIVE pueden iniciar sesión; DELETED es una eliminación lógica. */
enum class UserStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    ACTIVE("Activo", R.color.rs_green, R.color.rs_green_soft),
    PENDING("Pendiente", R.color.rs_yellow, R.color.rs_yellow_soft),
    BLOCKED("Bloqueado", R.color.rs_red, R.color.rs_red_soft),
    DELETED("Eliminado", R.color.rs_gray_text, R.color.rs_gray_light)
}

/**
 * Perfil de usuario guardado en `users/{uid}`.
 *
 * El `id` es el UID de Firebase Authentication. La contraseña nunca se guarda en Firestore:
 * la gestiona Firebase Authentication.
 */
data class User(
    val id: String,
    var name: String,
    var email: String,
    var document: String,
    var phone: String,
    var role: Role,
    var status: UserStatus,
    var address: String = "",
    var city: String = "Bogotá",
    var storeName: String = "",
    var biometricEnabled: Boolean = false,
    var marketingConsent: Boolean = false,
    var locationConsent: Boolean = false,
    var mustChangePassword: Boolean = false,
    var openSessions: Int = 1,
    val createdAt: String = ""
) {
    /** Iniciales del nombre para los avatares circulares. */
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

/** Categorías del catálogo con su ícono de respaldo cuando el producto no tiene foto. */
enum class Category(val label: String, @DrawableRes val icon: Int) {
    PHONES("Celulares", R.drawable.ic_dev_phone),
    LAPTOPS("Portátiles", R.drawable.ic_dev_laptop),
    TABLETS("Tabletas", R.drawable.ic_dev_tablet),
    AUDIO("Audio", R.drawable.ic_dev_audio),
    MONITORS("Monitores", R.drawable.ic_dev_monitor),
    WEARABLES("Relojes", R.drawable.ic_dev_watch)
}

/** Condición comercial del equipo (nuevo de saldo, reacondicionado, usado o liquidación). */
enum class Condition(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    NEW_SURPLUS("Nuevo (saldo)", R.color.rs_green, R.color.rs_green_soft),
    REFURBISHED("Reacondicionado", R.color.rs_blue, R.color.rs_blue_soft),
    USED("Usado", R.color.rs_graphite_soft, R.color.rs_gray_light),
    LIQUIDATION("Liquidación", R.color.rs_orange_dark, R.color.rs_orange_soft)
}

/** Grado estético del equipo, visible antes de pagar. */
enum class Grade(val label: String, val detail: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    A("Grado A", "Como nuevo, sin marcas visibles", R.color.rs_green, R.color.rs_green_soft),
    B("Grado B", "Marcas leves de uso", R.color.rs_yellow, R.color.rs_yellow_soft),
    C("Grado C", "Marcas visibles, 100% funcional", R.color.rs_red, R.color.rs_red_soft)
}

/** Estado de la publicación: solo PUBLISHED aparece en el catálogo del comprador. */
enum class ProductStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    PUBLISHED("Publicado", R.color.rs_green, R.color.rs_green_soft),
    REVIEW("En revisión", R.color.rs_yellow, R.color.rs_yellow_soft),
    REJECTED("Rechazado", R.color.rs_red, R.color.rs_red_soft),
    PAUSED("Pausado", R.color.rs_gray_text, R.color.rs_gray_light)
}

/**
 * Producto guardado en `products/{id}`.
 *
 * Las fotos se guardan comprimidas en Firestore (`productPhotos/{id}`) y aquí solo se guardan
 * sus referencias `firestore://productPhotos/{id}` en [photoUrls].
 */
data class Product(
    val id: String,
    var title: String,
    var brand: String,
    var category: Category,
    var condition: Condition,
    var grade: Grade,
    var price: Long,
    var oldPrice: Long,
    var stock: Int,
    var serial: String,
    var warrantyMonths: Int,
    var defects: String,
    var tests: String,
    var description: String,
    var sellerId: String,
    var status: ProductStatus,
    var sellerName: String = "",
    var photoUrls: List<String> = emptyList(),
    var rating: Double = 4.6,
    var rejectReason: String = ""
) {
    /** Número de fotos reales del producto. */
    val photos: Int get() = photoUrls.size

    /** Primera foto o null si el producto aún no tiene imágenes. */
    val coverUrl: String? get() = photoUrls.firstOrNull()

    /** Porcentaje de descuento frente al precio de referencia. */
    val discountPercent: Int
        get() = if (oldPrice > price && oldPrice > 0) (((oldPrice - price) * 100) / oldPrice).toInt() else 0

    /** Serial enmascarado para no exponerlo completo al comprador. */
    val maskedSerial: String
        get() = if (serial.length > 4) "***" + serial.takeLast(4) else serial
}

/** Línea del carrito; se persiste en `users/{uid}/cart/{productId}`. */
data class CartItem(val product: Product, var quantity: Int) {
    val subtotal: Long get() = product.price * quantity
}

/** Estados del pedido, desde el pago pendiente hasta la entrega o cancelación. */
enum class OrderStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    PENDING_PAYMENT("Pago pendiente", R.color.rs_yellow, R.color.rs_yellow_soft),
    PAID("Pagado", R.color.rs_blue, R.color.rs_blue_soft),
    PREPARING("En alistamiento", R.color.rs_orange_dark, R.color.rs_orange_soft),
    SHIPPED("Despachado", R.color.rs_purple, R.color.rs_gray_light),
    DELIVERED("Entregado", R.color.rs_green, R.color.rs_green_soft),
    CANCELLED("Cancelado", R.color.rs_red, R.color.rs_red_soft)
}

/** Línea de un pedido: copia los datos del producto al momento de la compra. */
data class OrderLine(
    val productId: String,
    val title: String,
    val category: Category,
    val grade: Grade,
    val unitPrice: Long,
    val quantity: Int,
    val sellerId: String
) {
    val subtotal: Long get() = unitPrice * quantity
}

/**
 * Pedido guardado en `orders/{id}`. Es el historial de compras del comprador y la bandeja de
 * despachos del vendedor. Guarda la ubicación capturada con GPS y la respuesta de ePayco.
 */
data class Order(
    val id: String,
    val buyerId: String,
    val buyerName: String,
    val buyerEmail: String,
    val lines: List<OrderLine>,
    val shipping: Long,
    var status: OrderStatus,
    val date: String,
    val createdAt: Long,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    var paymentMethod: String = "ePayco",
    var paymentRef: String = "",
    var transactionId: String = "",
    var paymentResponse: String = "",
    var carrier: String = "",
    var tracking: String = ""
) {
    val itemsTotal: Long get() = lines.sumOf { it.subtotal }
    val total: Long get() = itemsTotal + shipping

    /** Ids de los vendedores del pedido; se guardan en Firestore para consultar con array-contains. */
    val sellerIds: List<String> get() = lines.map { it.sellerId }.distinct()

    /** Indica si el pedido tiene coordenadas GPS asociadas a la dirección. */
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** Tipo de evento de auditoría, con su ícono y color. */
enum class AuditType(val label: String, @DrawableRes val icon: Int, @ColorRes val color: Int) {
    SECURITY("Seguridad", R.drawable.ic_shield, R.color.rs_red),
    USERS("Usuarios", R.drawable.ic_people, R.color.rs_blue),
    CATALOG("Catálogo", R.drawable.ic_inventory, R.color.rs_orange),
    PAYMENTS("Pagos", R.drawable.ic_payments, R.color.rs_green),
    ORDERS("Pedidos", R.drawable.ic_truck, R.color.rs_purple)
}

/** Evento de la bitácora de auditoría (`audit/{id}`). */
data class AuditEvent(
    val date: String,
    val actor: String,
    val action: String,
    val detail: String,
    val type: AuditType,
    val createdAt: Long = 0L
)

/** Resultado normalizado de una transacción de la pasarela. */
enum class PaymentResult { APPROVED, PENDING, REJECTED }
