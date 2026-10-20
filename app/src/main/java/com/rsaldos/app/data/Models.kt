package com.rsaldos.app.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.rsaldos.app.R

enum class Role(val label: String) {
    BUYER("Comprador"),
    SELLER("Vendedor"),
    ADMIN("Administrador")
}

enum class UserStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    ACTIVE("Activo", R.color.rs_green, R.color.rs_green_soft),
    PENDING("Pendiente", R.color.rs_yellow, R.color.rs_yellow_soft),
    BLOCKED("Bloqueado", R.color.rs_red, R.color.rs_red_soft),
    DELETED("Eliminado", R.color.rs_gray_text, R.color.rs_gray_light)
}

data class User(
    val id: Int,
    var name: String,
    var email: String,
    var password: String,
    var document: String,
    var phone: String,
    var role: Role,
    var status: UserStatus,
    var address: String = "",
    var city: String = "Bogotá",
    var storeName: String = "",
    var biometricEnabled: Boolean = true,
    var marketingConsent: Boolean = false,
    var locationConsent: Boolean = false,
    var mustChangePassword: Boolean = false,
    var openSessions: Int = 1,
    val createdAt: String = "10/10/2026"
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

enum class Category(val label: String, @DrawableRes val icon: Int) {
    PHONES("Celulares", R.drawable.ic_dev_phone),
    LAPTOPS("Portátiles", R.drawable.ic_dev_laptop),
    TABLETS("Tabletas", R.drawable.ic_dev_tablet),
    AUDIO("Audio", R.drawable.ic_dev_audio),
    MONITORS("Monitores", R.drawable.ic_dev_monitor),
    WEARABLES("Relojes", R.drawable.ic_dev_watch)
}

enum class Condition(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    NEW_SURPLUS("Nuevo (saldo)", R.color.rs_green, R.color.rs_green_soft),
    REFURBISHED("Reacondicionado", R.color.rs_blue, R.color.rs_blue_soft),
    USED("Usado", R.color.rs_graphite_soft, R.color.rs_gray_light),
    LIQUIDATION("Liquidación", R.color.rs_orange_dark, R.color.rs_orange_soft)
}

enum class Grade(val label: String, val detail: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    A("Grado A", "Como nuevo, sin marcas visibles", R.color.rs_green, R.color.rs_green_soft),
    B("Grado B", "Marcas leves de uso", R.color.rs_yellow, R.color.rs_yellow_soft),
    C("Grado C", "Marcas visibles, 100% funcional", R.color.rs_red, R.color.rs_red_soft)
}

enum class ProductStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    PUBLISHED("Publicado", R.color.rs_green, R.color.rs_green_soft),
    REVIEW("En revisión", R.color.rs_yellow, R.color.rs_yellow_soft),
    REJECTED("Rechazado", R.color.rs_red, R.color.rs_red_soft),
    PAUSED("Pausado", R.color.rs_gray_text, R.color.rs_gray_light)
}

data class Product(
    val id: Int,
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
    var sellerId: Int,
    var status: ProductStatus,
    var photos: Int = 3,
    var rating: Double = 4.6,
    var rejectReason: String = ""
) {
    val discountPercent: Int
        get() = if (oldPrice > price && oldPrice > 0) (((oldPrice - price) * 100) / oldPrice).toInt() else 0

    val maskedSerial: String
        get() = if (serial.length > 4) "***" + serial.takeLast(4) else serial
}

data class CartItem(val product: Product, var quantity: Int) {
    val subtotal: Long get() = product.price * quantity
}

enum class OrderStatus(val label: String, @ColorRes val color: Int, @ColorRes val bg: Int) {
    PENDING_PAYMENT("Pago pendiente", R.color.rs_yellow, R.color.rs_yellow_soft),
    PAID("Pagado", R.color.rs_blue, R.color.rs_blue_soft),
    PREPARING("En alistamiento", R.color.rs_orange_dark, R.color.rs_orange_soft),
    SHIPPED("Despachado", R.color.rs_purple, R.color.rs_gray_light),
    DELIVERED("Entregado", R.color.rs_green, R.color.rs_green_soft),
    CANCELLED("Cancelado", R.color.rs_red, R.color.rs_red_soft)
}

data class OrderLine(
    val productId: Int,
    val title: String,
    val category: Category,
    val grade: Grade,
    val unitPrice: Long,
    val quantity: Int,
    val sellerId: Int
) {
    val subtotal: Long get() = unitPrice * quantity
}

data class Order(
    val id: String,
    val buyerId: Int,
    val lines: List<OrderLine>,
    val shipping: Long,
    var status: OrderStatus,
    val date: String,
    val address: String,
    val paymentMethod: String,
    val paymentRef: String,
    var carrier: String = "",
    var tracking: String = ""
) {
    val itemsTotal: Long get() = lines.sumOf { it.subtotal }
    val total: Long get() = itemsTotal + shipping
}

enum class AuditType(val label: String, @DrawableRes val icon: Int, @ColorRes val color: Int) {
    SECURITY("Seguridad", R.drawable.ic_shield, R.color.rs_red),
    USERS("Usuarios", R.drawable.ic_people, R.color.rs_blue),
    CATALOG("Catálogo", R.drawable.ic_inventory, R.color.rs_orange),
    PAYMENTS("Pagos", R.drawable.ic_payments, R.color.rs_green),
    ORDERS("Pedidos", R.drawable.ic_truck, R.color.rs_purple)
}

data class AuditEvent(
    val date: String,
    val actor: String,
    val action: String,
    val detail: String,
    val type: AuditType
)

enum class PaymentResult { APPROVED, PENDING, REJECTED }
