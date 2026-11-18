package com.rsaldos.app.data

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Conversión entre los modelos de dominio y los documentos de Cloud Firestore.
 *
 * Se usa un mapeo manual (en lugar de `toObject`) para controlar los nombres de campo,
 * guardar los enums por nombre y tolerar documentos incompletos sin que la app se cierre.
 */

/** Lee un enum por nombre y devuelve [default] si el valor no existe. */
inline fun <reified T : Enum<T>> enumOf(value: Any?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

/** Lectores tolerantes: devuelven un valor por defecto si el campo falta. */
private fun DocumentSnapshot.str(field: String): String = getString(field).orEmpty()
private fun DocumentSnapshot.long(field: String): Long = getLong(field) ?: 0L
private fun DocumentSnapshot.int(field: String): Int = (getLong(field) ?: 0L).toInt()
private fun DocumentSnapshot.bool(field: String): Boolean = getBoolean(field) ?: false

/** Convierte un [User] en el mapa que se guarda en `users/{uid}`. */
fun User.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "email" to email.lowercase(),
    "document" to document,
    "phone" to phone,
    "role" to role.name,
    "status" to status.name,
    "address" to address,
    "city" to city,
    "storeName" to storeName,
    "biometricEnabled" to biometricEnabled,
    "marketingConsent" to marketingConsent,
    "locationConsent" to locationConsent,
    "mustChangePassword" to mustChangePassword,
    "openSessions" to openSessions,
    "createdAt" to createdAt
)

/** Construye un [User] a partir de su documento de Firestore. */
fun DocumentSnapshot.toUser(): User = User(
    id = id,
    name = str("name"),
    email = str("email"),
    document = str("document"),
    phone = str("phone"),
    role = enumOf(get("role"), Role.BUYER),
    status = enumOf(get("status"), UserStatus.ACTIVE),
    address = str("address"),
    city = str("city").ifBlank { "Bogotá" },
    storeName = str("storeName"),
    biometricEnabled = bool("biometricEnabled"),
    marketingConsent = bool("marketingConsent"),
    locationConsent = bool("locationConsent"),
    mustChangePassword = bool("mustChangePassword"),
    openSessions = int("openSessions"),
    createdAt = str("createdAt")
)

/** Convierte un [Product] en el mapa que se guarda en `products/{id}`. */
fun Product.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "brand" to brand,
    "category" to category.name,
    "condition" to condition.name,
    "grade" to grade.name,
    "price" to price,
    "oldPrice" to oldPrice,
    "stock" to stock,
    "serial" to serial,
    "warrantyMonths" to warrantyMonths,
    "defects" to defects,
    "tests" to tests,
    "description" to description,
    "sellerId" to sellerId,
    "sellerName" to sellerName,
    "status" to status.name,
    "photoUrls" to photoUrls,
    "rating" to rating,
    "rejectReason" to rejectReason
)

/** Construye un [Product] a partir de su documento de Firestore. */
@Suppress("UNCHECKED_CAST")
fun DocumentSnapshot.toProduct(): Product = Product(
    id = id,
    title = str("title"),
    brand = str("brand"),
    category = enumOf(get("category"), Category.PHONES),
    condition = enumOf(get("condition"), Condition.USED),
    grade = enumOf(get("grade"), Grade.A),
    price = long("price"),
    oldPrice = long("oldPrice"),
    stock = int("stock"),
    serial = str("serial"),
    warrantyMonths = int("warrantyMonths"),
    defects = str("defects"),
    tests = str("tests"),
    description = str("description"),
    sellerId = str("sellerId"),
    status = enumOf(get("status"), ProductStatus.REVIEW),
    sellerName = str("sellerName"),
    photoUrls = (get("photoUrls") as? List<String>).orEmpty(),
    rating = getDouble("rating") ?: 0.0,
    rejectReason = str("rejectReason")
)

/** Convierte una línea de pedido en un mapa anidado. */
private fun OrderLine.toMap(): Map<String, Any?> = mapOf(
    "productId" to productId,
    "title" to title,
    "category" to category.name,
    "grade" to grade.name,
    "unitPrice" to unitPrice,
    "quantity" to quantity,
    "sellerId" to sellerId
)

/** Lee una línea de pedido desde el mapa anidado de Firestore. */
private fun Map<*, *>.toOrderLine(): OrderLine = OrderLine(
    productId = this["productId"] as? String ?: "",
    title = this["title"] as? String ?: "",
    category = enumOf(this["category"], Category.PHONES),
    grade = enumOf(this["grade"], Grade.A),
    unitPrice = (this["unitPrice"] as? Number)?.toLong() ?: 0L,
    quantity = (this["quantity"] as? Number)?.toInt() ?: 0,
    sellerId = this["sellerId"] as? String ?: ""
)

/** Convierte un [Order] en el mapa que se guarda en `orders/{id}`. */
fun Order.toMap(): Map<String, Any?> = mapOf(
    "buyerId" to buyerId,
    "buyerName" to buyerName,
    "buyerEmail" to buyerEmail,
    "lines" to lines.map { it.toMap() },
    "sellerIds" to sellerIds,
    "shipping" to shipping,
    "total" to total,
    "status" to status.name,
    "date" to date,
    "createdAt" to createdAt,
    "address" to address,
    "latitude" to latitude,
    "longitude" to longitude,
    "paymentMethod" to paymentMethod,
    "paymentRef" to paymentRef,
    "transactionId" to transactionId,
    "paymentResponse" to paymentResponse,
    "carrier" to carrier,
    "tracking" to tracking
)

/** Construye un [Order] a partir de su documento de Firestore. */
fun DocumentSnapshot.toOrder(): Order = Order(
    id = id,
    buyerId = str("buyerId"),
    buyerName = str("buyerName"),
    buyerEmail = str("buyerEmail"),
    lines = (get("lines") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toOrderLine() },
    shipping = long("shipping"),
    status = enumOf(get("status"), OrderStatus.PENDING_PAYMENT),
    date = str("date"),
    createdAt = long("createdAt"),
    address = str("address"),
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    paymentMethod = str("paymentMethod"),
    paymentRef = str("paymentRef"),
    transactionId = str("transactionId"),
    paymentResponse = str("paymentResponse"),
    carrier = str("carrier"),
    tracking = str("tracking")
)

/** Convierte un evento de auditoría en mapa. */
fun AuditEvent.toMap(actorId: String): Map<String, Any?> = mapOf(
    "date" to date,
    "actor" to actor,
    "actorId" to actorId,
    "action" to action,
    "detail" to detail,
    "type" to type.name,
    "createdAt" to createdAt
)

/** Construye un [AuditEvent] desde Firestore. */
fun DocumentSnapshot.toAuditEvent(): AuditEvent = AuditEvent(
    date = str("date"),
    actor = str("actor"),
    action = str("action"),
    detail = str("detail"),
    type = enumOf(get("type"), AuditType.SECURITY),
    createdAt = long("createdAt")
)
