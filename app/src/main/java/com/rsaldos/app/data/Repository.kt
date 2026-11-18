package com.rsaldos.app.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Repositorio central de datos de R-Saldos sobre Cloud Firestore.
 *
 * Funciona como una caché en memoria que se mantiene sincronizada en tiempo real con
 * Firestore mediante *snapshot listeners*. Las pantallas leen la caché de forma síncrona
 * (listas siempre actualizadas) y se suscriben con [addObserver] para refrescarse cada vez
 * que llega un cambio desde la nube. Las escrituras van directo a Firestore; gracias a la
 * compensación de latencia del SDK, los listeners reciben el cambio local de inmediato.
 *
 * Colecciones:
 * - `users/{uid}`: perfiles; subcolección `cart/{productId}` con el carrito de cada comprador.
 * - `products/{id}`: catálogo e inventario.
 * - `orders/{id}`: pedidos con líneas, dirección, coordenadas GPS y datos de pago.
 * - `audit/{id}`: bitácora de acciones relevantes.
 *
 * Los listeners que se abren dependen del rol, para que cada usuario solo descargue los
 * documentos que las reglas de seguridad le permiten leer.
 */
object Repository {
    private const val TAG = "Repository"
    const val USERS = "users"
    const val PRODUCTS = "products"
    const val ORDERS = "orders"
    const val AUDIT = "audit"
    const val CART = "cart"

    /** Código del segundo factor del administrador (demostración de MFA). */
    const val DEMO_MFA_CODE = "246810"

    /** Costo fijo del envío estándar en pesos colombianos. */
    const val SHIPPING_COST = 12_000L

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val users = mutableListOf<User>()
    private val products = mutableListOf<Product>()
    private val orders = mutableListOf<Order>()
    private val audit = mutableListOf<AuditEvent>()
    private val cartQuantities = linkedMapOf<String, Int>()

    /** Carrito del comprador actual, reconstruido a partir de Firestore y del catálogo. */
    val cart = mutableListOf<CartItem>()

    /** Perfil del usuario con sesión iniciada; null si no hay sesión. */
    var me: User? = null
        private set

    private val registrations = mutableListOf<ListenerRegistration>()
    private val observers = CopyOnWriteArraySet<() -> Unit>()

    /** Se invoca cuando el administrador bloquea o elimina la cuenta con la sesión abierta. */
    var onSessionRevoked: ((UserStatus) -> Unit)? = null

    /** Indica si Firebase quedó inicializado (requiere app/google-services.json). */
    fun isFirebaseReady(context: android.content.Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    // ---------------------------------------------------------------------------------------
    // Sincronización en tiempo real
    // ---------------------------------------------------------------------------------------

    /** Registra una función que se ejecuta cada vez que cambia algún dato de la caché. */
    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    /** Elimina un observador registrado con [addObserver]. */
    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    /** Notifica a todas las pantallas suscritas que hay datos nuevos. */
    private fun changed() = observers.forEach { it() }

    /** Lee una vez el perfil de un usuario desde Firestore. */
    suspend fun fetchProfile(uid: String): User? {
        val doc = db.collection(USERS).document(uid).get().await()
        return if (doc.exists()) doc.toUser() else null
    }

    /**
     * Abre los listeners de Firestore para el usuario que acaba de iniciar sesión.
     * Comprador: su perfil, carrito, catálogo y sus pedidos. Vendedor: perfil, catálogo y
     * pedidos que incluyen sus productos. Administrador: todo, más usuarios y auditoría.
     */
    fun start(user: User) {
        stop()
        me = user
        val uid = user.id

        registrations += db.collection(USERS).document(uid).addSnapshotListener { snap, error ->
            if (error != null) { Log.w(TAG, "perfil", error); return@addSnapshotListener }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val updated = snap.toUser()
            me = updated
            replaceUser(updated)
            changed()
            if (updated.status == UserStatus.BLOCKED || updated.status == UserStatus.DELETED) {
                onSessionRevoked?.invoke(updated.status)
            }
        }

        registrations += db.collection(PRODUCTS).addSnapshotListener { snap, error ->
            if (error != null) { Log.w(TAG, "productos", error); return@addSnapshotListener }
            products.clear()
            snap?.documents?.forEach { products += it.toProduct() }
            products.sortBy { it.title }
            rebuildCart()
            changed()
        }

        val ordersQuery: Query = when (user.role) {
            Role.BUYER -> db.collection(ORDERS).whereEqualTo("buyerId", uid)
            Role.SELLER -> db.collection(ORDERS).whereArrayContains("sellerIds", uid)
            Role.ADMIN -> db.collection(ORDERS)
        }
        registrations += ordersQuery.addSnapshotListener { snap, error ->
            if (error != null) { Log.w(TAG, "pedidos", error); return@addSnapshotListener }
            orders.clear()
            snap?.documents?.forEach { orders += it.toOrder() }
            changed()
        }

        if (user.role == Role.BUYER) {
            registrations += cartRef(uid).addSnapshotListener { snap, error ->
                if (error != null) { Log.w(TAG, "carrito", error); return@addSnapshotListener }
                cartQuantities.clear()
                snap?.documents?.forEach { doc ->
                    cartQuantities[doc.id] = (doc.getLong("quantity") ?: 0L).toInt()
                }
                rebuildCart()
                changed()
            }
        }

        if (user.role == Role.ADMIN) {
            registrations += db.collection(USERS).addSnapshotListener { snap, error ->
                if (error != null) { Log.w(TAG, "usuarios", error); return@addSnapshotListener }
                users.clear()
                snap?.documents?.forEach { users += it.toUser() }
                users.sortBy { it.name }
                changed()
            }
            registrations += db.collection(AUDIT).orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
                .addSnapshotListener { snap, error ->
                    if (error != null) { Log.w(TAG, "auditoria", error); return@addSnapshotListener }
                    audit.clear()
                    snap?.documents?.forEach { audit += it.toAuditEvent() }
                    changed()
                }
        } else {
            users.clear()
            users += user
        }
    }

    /** Cierra todos los listeners y limpia la caché (al cerrar sesión). */
    fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
        users.clear(); products.clear(); orders.clear(); audit.clear()
        cartQuantities.clear(); cart.clear()
        me = null
    }

    /** Reemplaza un usuario en la caché o lo agrega si no existe. */
    private fun replaceUser(user: User) {
        val index = users.indexOfFirst { it.id == user.id }
        if (index >= 0) users[index] = user else users += user
    }

    /** Une las cantidades del carrito con los productos del catálogo cargado. */
    private fun rebuildCart() {
        cart.clear()
        cartQuantities.forEach { (productId, qty) ->
            products.firstOrNull { it.id == productId }?.let { cart += CartItem(it, qty) }
        }
    }

    /** Subcolección del carrito de un usuario. */
    private fun cartRef(uid: String) = db.collection(USERS).document(uid).collection(CART)

    // ---------------------------------------------------------------------------------------
    // Usuarios (CRUD del administrador y perfil propio)
    // ---------------------------------------------------------------------------------------

    /** Busca un usuario en la caché. */
    fun findUser(id: String): User? = users.firstOrNull { it.id == id } ?: me?.takeIf { it.id == id }

    /** Indica si el correo ya está registrado en otra cuenta no eliminada. */
    fun emailExists(email: String, exceptId: String = ""): Boolean =
        users.any { it.email.equals(email.trim(), ignoreCase = true) && it.id != exceptId && it.status != UserStatus.DELETED }

    /** Usuarios visibles para el administrador (excluye los eliminados lógicamente). */
    fun allUsers(): List<User> = users.filter { it.status != UserStatus.DELETED }

    /** Guarda el perfil de un usuario nuevo en `users/{uid}` (registro o creación por el administrador). */
    suspend fun saveNewProfile(user: User) {
        db.collection(USERS).document(user.id).set(user.toMap()).await()
    }

    /**
     * Crea un usuario desde el rol Administrador.
     *
     * Se usa una segunda instancia de FirebaseApp para crear la cuenta en Firebase
     * Authentication sin cerrar la sesión del administrador; luego se guarda el perfil.
     */
    suspend fun createUser(actor: User, user: User, password: String): User {
        val uid = AuthService.createAccountAsAdmin(user.email, password)
        val created = user.copy(id = uid, createdAt = today())
        saveNewProfile(created)
        log(actor.name, "Creó usuario", "${created.email} · ${created.role.label}", AuditType.USERS)
        return created
    }

    /** Actualiza un usuario (edición, aprobación, bloqueo) y deja el motivo en la auditoría. */
    fun updateUser(actor: User, user: User, reason: String) {
        replaceUser(user)
        db.collection(USERS).document(user.id).set(user.toMap(), SetOptions.merge())
        log(actor.name, "Editó usuario", "${user.email} · ${user.status.label} · motivo: $reason", AuditType.USERS)
        changed()
    }

    /** Eliminación lógica: marca la cuenta como DELETED; la sesión abierta se cierra sola. */
    fun deleteUser(actor: User, user: User, reason: String) {
        user.status = UserStatus.DELETED
        user.openSessions = 0
        db.collection(USERS).document(user.id)
            .update(mapOf("status" to UserStatus.DELETED.name, "openSessions" to 0))
        log(actor.name, "Eliminó usuario (lógico)", "${user.email} · motivo: $reason", AuditType.USERS)
        changed()
    }

    /** Guarda los cambios que el propio usuario hace en su perfil. */
    fun updateProfile(user: User, detail: String) {
        replaceUser(user)
        me = user
        db.collection(USERS).document(user.id).set(user.toMap(), SetOptions.merge())
        log(user.name, "Actualizó su perfil", detail, AuditType.USERS)
        changed()
    }

    // ---------------------------------------------------------------------------------------
    // Productos (CRUD del vendedor y del administrador)
    // ---------------------------------------------------------------------------------------

    /** Genera un id de documento nuevo para un producto (se usa también como prefijo de sus fotos). */
    fun newProductId(): String = db.collection(PRODUCTS).document().id

    /** Busca un producto en la caché. */
    fun product(id: String): Product? = products.firstOrNull { it.id == id }

    /** Todos los productos (administrador). */
    fun allProducts(): List<Product> = products.toList()

    /** Productos visibles en el catálogo: publicados y con existencias. */
    fun publishedProducts(): List<Product> =
        products.filter { it.status == ProductStatus.PUBLISHED && it.stock > 0 }

    /** Productos de un vendedor. */
    fun productsOfSeller(sellerId: String): List<Product> = products.filter { it.sellerId == sellerId }

    /**
     * Busca en el catálogo publicado por texto, categoría, grados y precio máximo.
     * `sort`: 0 relevancia, 1 menor precio, 2 mayor precio, 3 mayor descuento.
     */
    fun search(query: String, category: Category?, grades: Set<Grade>, maxPrice: Long?, sort: Int): List<Product> {
        val q = query.trim().lowercase()
        var list = publishedProducts().filter { p ->
            (q.isEmpty() || p.title.lowercase().contains(q) || p.brand.lowercase().contains(q) ||
                p.category.label.lowercase().contains(q)) &&
                (category == null || p.category == category) &&
                (grades.isEmpty() || p.grade in grades) &&
                (maxPrice == null || p.price <= maxPrice)
        }
        list = when (sort) {
            1 -> list.sortedBy { it.price }
            2 -> list.sortedByDescending { it.price }
            3 -> list.sortedByDescending { it.discountPercent }
            else -> list
        }
        return list
    }

    /**
     * Crea o actualiza un producto. Si lo edita un vendedor vuelve a revisión; si lo crea el
     * administrador queda publicado directamente.
     */
    suspend fun saveProduct(actor: User, product: Product): Product {
        val old = product(product.id)
        val saved = if (old != null) {
            if (actor.role == Role.SELLER) product.status = ProductStatus.REVIEW
            product
        } else {
            product.copy(status = if (actor.role == Role.ADMIN) ProductStatus.PUBLISHED else ProductStatus.REVIEW)
        }
        db.collection(PRODUCTS).document(saved.id).set(saved.toMap()).await()
        val detail = if (old != null && old.price != saved.price) {
            "${saved.title} · precio ${Format.money(old.price)} → ${Format.money(saved.price)}"
        } else saved.title
        log(actor.name, if (old != null) "Editó producto" else "Creó producto", detail, AuditType.CATALOG)
        return saved
    }

    /** Elimina el producto de Firestore, sus fotos y lo saca del carrito propio. */
    fun deleteProduct(actor: User, product: Product) {
        products.removeAll { it.id == product.id }
        db.collection(PRODUCTS).document(product.id).delete()
        StorageService.deleteProductPhotos(product)
        me?.let { if (it.role == Role.BUYER) cartRef(it.id).document(product.id).delete() }
        rebuildCart()
        log(actor.name, "Eliminó producto", product.title, AuditType.CATALOG)
        changed()
    }

    /** El administrador aprueba la publicación. */
    fun approveProduct(actor: User, product: Product) {
        product.status = ProductStatus.PUBLISHED
        product.rejectReason = ""
        db.collection(PRODUCTS).document(product.id)
            .update(mapOf("status" to ProductStatus.PUBLISHED.name, "rejectReason" to ""))
        log(actor.name, "Aprobó publicación", product.title, AuditType.CATALOG)
        changed()
    }

    /** El administrador rechaza la publicación indicando el motivo. */
    fun rejectProduct(actor: User, product: Product, reason: String) {
        product.status = ProductStatus.REJECTED
        product.rejectReason = reason
        db.collection(PRODUCTS).document(product.id)
            .update(mapOf("status" to ProductStatus.REJECTED.name, "rejectReason" to reason))
        log(actor.name, "Rechazó publicación", "${product.title} · $reason", AuditType.CATALOG)
        changed()
    }

    /** Pausa o reanuda una publicación (al reanudar vuelve a revisión). */
    fun togglePause(actor: User, product: Product) {
        product.status = if (product.status == ProductStatus.PAUSED) ProductStatus.REVIEW else ProductStatus.PAUSED
        db.collection(PRODUCTS).document(product.id).update("status", product.status.name)
        log(actor.name, "Cambió estado de publicación", "${product.title} → ${product.status.label}", AuditType.CATALOG)
        changed()
    }

    // ---------------------------------------------------------------------------------------
    // Carrito (CRUD en users/{uid}/cart)
    // ---------------------------------------------------------------------------------------

    /** Unidades en el carrito. */
    fun cartCount(): Int = cart.sumOf { it.quantity }

    /** Subtotal del carrito sin envío. */
    fun cartTotal(): Long = cart.sumOf { it.subtotal }

    /** Agrega unidades al carrito si hay existencias; devuelve false si se supera el stock. */
    fun addToCart(product: Product, quantity: Int = 1): Boolean {
        val uid = me?.id ?: return false
        val current = cartQuantities[product.id] ?: 0
        if (current + quantity > product.stock) return false
        cartQuantities[product.id] = current + quantity
        rebuildCart()
        cartRef(uid).document(product.id).set(
            mapOf("productId" to product.id, "quantity" to current + quantity, "updatedAt" to FieldValue.serverTimestamp())
        )
        changed()
        return true
    }

    /** Cambia la cantidad de una línea del carrito respetando el stock disponible. */
    fun updateQuantity(item: CartItem, quantity: Int): Boolean {
        val uid = me?.id ?: return false
        if (quantity < 1 || quantity > item.product.stock) return false
        item.quantity = quantity
        cartQuantities[item.product.id] = quantity
        cartRef(uid).document(item.product.id).update("quantity", quantity)
        changed()
        return true
    }

    /** Elimina una línea del carrito. */
    fun removeFromCart(item: CartItem) {
        val uid = me?.id ?: return
        cartQuantities.remove(item.product.id)
        rebuildCart()
        cartRef(uid).document(item.product.id).delete()
        changed()
    }

    /** Vacía el carrito con una escritura en lote. */
    fun clearCart() {
        val uid = me?.id ?: return
        val ids = cartQuantities.keys.toList()
        cartQuantities.clear()
        cart.clear()
        val batch = db.batch()
        ids.forEach { batch.delete(cartRef(uid).document(it)) }
        batch.commit()
        changed()
    }

    // ---------------------------------------------------------------------------------------
    // Pedidos y pagos
    // ---------------------------------------------------------------------------------------

    /** Pedidos de un comprador, del más reciente al más antiguo. */
    fun ordersOfBuyer(buyerId: String): List<Order> =
        orders.filter { it.buyerId == buyerId }.sortedByDescending { it.createdAt }

    /** Pedidos que incluyen productos del vendedor. */
    fun ordersOfSeller(sellerId: String): List<Order> =
        orders.filter { o -> o.lines.any { it.sellerId == sellerId } }.sortedByDescending { it.createdAt }

    /** Todos los pedidos (administrador). */
    fun allOrders(): List<Order> = orders.sortedByDescending { it.createdAt }

    /** Busca un pedido en la caché. */
    fun order(id: String): Order? = orders.firstOrNull { it.id == id }

    /** Consulta un pedido directamente en Firestore (por si aún no llegó a la caché). */
    suspend fun fetchOrder(id: String): Order? {
        val doc = db.collection(ORDERS).document(id).get().await()
        return if (doc.exists()) doc.toOrder() else null
    }

    /**
     * Crea el pedido en estado PENDING_PAYMENT con el contenido del carrito, la dirección y
     * las coordenadas capturadas. El número de pedido se usa como factura (`invoice`) en ePayco.
     */
    suspend fun createPendingOrder(
        buyer: User, address: String, latitude: Double?, longitude: Double?, shipping: Long
    ): Order {
        val lines = cart.map {
            OrderLine(it.product.id, it.product.title, it.product.category, it.product.grade,
                it.product.price, it.quantity, it.product.sellerId)
        }
        val now = System.currentTimeMillis()
        val id = "RS-" + SimpleDateFormat("yyMMddHHmmss", Locale.US).format(Date(now)) + (10..99).random()
        val order = Order(
            id = id, buyerId = buyer.id, buyerName = buyer.name, buyerEmail = buyer.email,
            lines = lines, shipping = shipping, status = OrderStatus.PENDING_PAYMENT,
            date = today(), createdAt = now, address = address,
            latitude = latitude, longitude = longitude, paymentMethod = "ePayco"
        )
        db.collection(ORDERS).document(id).set(order.toMap()).await()
        log(buyer.name, "Creó pedido", "$id · ${Format.money(order.total)} · pago pendiente", AuditType.ORDERS)
        return order
    }

    /**
     * Registra la respuesta de ePayco sobre el pedido.
     *
     * Si la transacción fue aprobada, una transacción de Firestore descuenta el inventario y
     * marca el pedido como PAID de forma atómica; luego se vacía el carrito. Si fue rechazada
     * el pedido queda CANCELLED y si está pendiente sigue en PENDING_PAYMENT.
     */
    suspend fun confirmPayment(orderId: String, tx: EpaycoTransaction): Order? {
        val orderRef = db.collection(ORDERS).document(orderId)
        val result = tx.result
        val paymentFields = mapOf(
            "paymentRef" to tx.refPayco,
            "transactionId" to tx.transactionId,
            "paymentMethod" to tx.methodLabel,
            "paymentResponse" to tx.responseText
        )
        if (result == PaymentResult.APPROVED) {
            db.runTransaction { transaction ->
                val snap = transaction.get(orderRef)
                val order = snap.toOrder()
                if (order.status != OrderStatus.PENDING_PAYMENT) return@runTransaction null
                val productRefs = order.lines.map { db.collection(PRODUCTS).document(it.productId) }
                val productSnaps = productRefs.map { transaction.get(it) }
                order.lines.zip(productSnaps).forEach { (line, ps) ->
                    if (ps.exists()) {
                        val stock = (ps.getLong("stock") ?: 0L).toInt()
                        transaction.update(ps.reference, "stock", (stock - line.quantity).coerceAtLeast(0))
                    }
                }
                transaction.update(orderRef, paymentFields + ("status" to OrderStatus.PAID.name))
                null
            }.await()
            clearCart()
        } else {
            val status = if (result == PaymentResult.REJECTED) OrderStatus.CANCELLED else OrderStatus.PENDING_PAYMENT
            orderRef.update(paymentFields + ("status" to status.name)).await()
        }
        val updated = fetchOrder(orderId)
        me?.let {
            log(it.name, "Pago ${result.name.lowercase()}",
                "$orderId · ref ePayco ${tx.refPayco} · ${Format.money(updated?.total ?: 0)}", AuditType.PAYMENTS)
        }
        return updated
    }

    /** El vendedor registra transportadora y guía y el pedido pasa a SHIPPED. */
    fun dispatchOrder(actor: User, order: Order, carrier: String, tracking: String) {
        order.carrier = carrier
        order.tracking = tracking
        order.status = OrderStatus.SHIPPED
        db.collection(ORDERS).document(order.id).update(
            mapOf("carrier" to carrier, "tracking" to tracking, "status" to OrderStatus.SHIPPED.name)
        )
        log(actor.name, "Despachó pedido", "${order.id} · $carrier guía $tracking", AuditType.ORDERS)
        changed()
    }

    /** Cambia el estado de un pedido (alistamiento, entrega, cancelación). */
    fun setOrderStatus(actor: User, order: Order, status: OrderStatus) {
        order.status = status
        db.collection(ORDERS).document(order.id).update("status", status.name)
        log(actor.name, "Cambió estado de pedido", "${order.id} → ${status.label}", AuditType.ORDERS)
        changed()
    }

    // ---------------------------------------------------------------------------------------
    // Reportes
    // ---------------------------------------------------------------------------------------

    /** Total vendido (excluye pedidos cancelados y sin pagar). */
    fun salesTotal(list: List<Order> = orders): Long = list.filter { isSale(it) }.sumOf { it.total }

    /** Ventas agrupadas por categoría para las gráficas de barras. */
    fun salesByCategory(): Map<Category, Long> {
        val map = linkedMapOf<Category, Long>()
        Category.values().forEach { map[it] = 0L }
        orders.filter { isSale(it) }.flatMap { it.lines }
            .forEach { map[it.category] = (map[it.category] ?: 0L) + it.subtotal }
        return map
    }

    /** Los cinco productos más vendidos por unidades. */
    fun topProducts(): List<Pair<String, Int>> =
        orders.filter { isSale(it) }.flatMap { it.lines }
            .groupBy { it.title }
            .map { (title, lines) -> title to lines.sumOf { it.quantity } }
            .sortedByDescending { it.second }
            .take(5)

    /** Eventos de auditoría, del más reciente al más antiguo. */
    fun auditLog(): List<AuditEvent> = audit.toList()

    /** Indica si el pedido se hizo dentro de los últimos `days` días. */
    fun inPeriod(order: Order, days: Int): Boolean {
        if (order.createdAt <= 0L) return false
        val diffDays = (System.currentTimeMillis() - order.createdAt) / (24L * 60 * 60 * 1000)
        return diffDays in 0..days.toLong()
    }

    /** Un pedido cuenta como venta cuando ya está pagado. */
    fun isSale(order: Order): Boolean =
        order.status != OrderStatus.CANCELLED && order.status != OrderStatus.PENDING_PAYMENT

    // ---------------------------------------------------------------------------------------
    // Auditoría
    // ---------------------------------------------------------------------------------------

    /** Escribe un evento en la bitácora `audit` (solo si hay una sesión de Firebase activa). */
    fun log(actor: String, action: String, detail: String, type: AuditType) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val event = AuditEvent(now(), actor, action, detail, type, System.currentTimeMillis())
        db.collection(AUDIT).add(event.toMap(uid))
            .addOnFailureListener { Log.w(TAG, "No se pudo registrar la auditoría", it) }
    }

    /** Fecha actual en formato dd/MM/yyyy (Colombia). */
    fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO")).format(Date())

    /** Fecha y hora actual para la auditoría. */
    private fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CO")).format(Date())
}
