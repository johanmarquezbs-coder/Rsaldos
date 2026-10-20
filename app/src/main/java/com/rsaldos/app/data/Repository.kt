package com.rsaldos.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Repository {
    private val users = mutableListOf<User>()
    private val products = mutableListOf<Product>()
    private val orders = mutableListOf<Order>()
    private val audit = mutableListOf<AuditEvent>()
    val cart = mutableListOf<CartItem>()

    private var nextUserId = 100
    private var nextProductId = 100
    private var nextOrder = 2051

    const val DEMO_MFA_CODE = "246810"
    const val DEMO_RECOVERY_CODE = "135790"
    const val SHIPPING_COST = 12_000L

    init {
        seed()
    }

    fun login(email: String, password: String): User? =
        users.firstOrNull {
            it.email.equals(email.trim(), ignoreCase = true) && it.password == password &&
                it.status != UserStatus.DELETED
        }

    fun findUserByEmail(email: String): User? =
        users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) && it.status != UserStatus.DELETED }

    fun findUser(id: Int): User? = users.firstOrNull { it.id == id }

    fun emailExists(email: String, exceptId: Int = -1): Boolean =
        users.any { it.email.equals(email.trim(), ignoreCase = true) && it.id != exceptId && it.status != UserStatus.DELETED }

    fun allUsers(): List<User> = users.filter { it.status != UserStatus.DELETED }

    fun register(
        name: String, email: String, password: String, document: String, phone: String,
        role: Role, marketing: Boolean
    ): User {
        val status = if (role == Role.SELLER) UserStatus.PENDING else UserStatus.ACTIVE
        val user = User(
            nextUserId++, name, email.trim(), password, document, phone, role, status,
            marketingConsent = marketing, createdAt = today()
        )
        users.add(user)
        log(name, "Registro de cuenta", "Rol ${role.label} · estado ${status.label}", AuditType.USERS)
        return user
    }

    fun createUser(actor: User, user: User): User {
        val created = user.copy(id = nextUserId++, createdAt = today())
        users.add(created)
        log(actor.name, "Creó usuario", "${created.email} · ${created.role.label}", AuditType.USERS)
        return created
    }

    fun updateUser(actor: User, user: User, reason: String) {
        val index = users.indexOfFirst { it.id == user.id }
        if (index >= 0) users[index] = user
        log(actor.name, "Editó usuario", "${user.email} · motivo: $reason", AuditType.USERS)
    }

    fun deleteUser(actor: User, user: User, reason: String) {
        user.status = UserStatus.DELETED
        user.openSessions = 0
        log(actor.name, "Eliminó usuario (lógico)", "${user.email} · motivo: $reason", AuditType.USERS)
    }

    fun changePassword(user: User, newPassword: String) {
        user.password = newPassword
        user.mustChangePassword = false
        log(user.name, "Cambio de contraseña", "Por recuperación o perfil", AuditType.SECURITY)
    }

    fun product(id: Int): Product? = products.firstOrNull { it.id == id }

    fun allProducts(): List<Product> = products.toList()

    fun publishedProducts(): List<Product> =
        products.filter { it.status == ProductStatus.PUBLISHED && it.stock > 0 }

    fun productsOfSeller(sellerId: Int): List<Product> = products.filter { it.sellerId == sellerId }

    fun search(
        query: String,
        category: Category?,
        grades: Set<Grade>,
        maxPrice: Long?,
        sort: Int
    ): List<Product> {
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

    fun saveProduct(actor: User, product: Product): Product {
        val index = products.indexOfFirst { it.id == product.id }
        return if (index >= 0) {
            val old = products[index]
            if (actor.role == Role.SELLER) product.status = ProductStatus.REVIEW
            products[index] = product
            val detail = if (old.price != product.price) {
                "${product.title} · precio ${Format.money(old.price)} → ${Format.money(product.price)}"
            } else product.title
            log(actor.name, "Editó producto", detail, AuditType.CATALOG)
            product
        } else {
            val created = product.copy(
                id = nextProductId++,
                status = if (actor.role == Role.ADMIN) ProductStatus.PUBLISHED else ProductStatus.REVIEW
            )
            products.add(created)
            log(actor.name, "Creó producto", created.title, AuditType.CATALOG)
            created
        }
    }

    fun deleteProduct(actor: User, product: Product) {
        products.removeAll { it.id == product.id }
        cart.removeAll { it.product.id == product.id }
        log(actor.name, "Eliminó producto", product.title, AuditType.CATALOG)
    }

    fun approveProduct(actor: User, product: Product) {
        product.status = ProductStatus.PUBLISHED
        product.rejectReason = ""
        log(actor.name, "Aprobó publicación", product.title, AuditType.CATALOG)
    }

    fun rejectProduct(actor: User, product: Product, reason: String) {
        product.status = ProductStatus.REJECTED
        product.rejectReason = reason
        log(actor.name, "Rechazó publicación", "${product.title} · $reason", AuditType.CATALOG)
    }

    fun togglePause(actor: User, product: Product) {
        product.status = if (product.status == ProductStatus.PAUSED) ProductStatus.REVIEW else ProductStatus.PAUSED
        log(actor.name, "Cambió estado de publicación", "${product.title} → ${product.status.label}", AuditType.CATALOG)
    }

    fun cartCount(): Int = cart.sumOf { it.quantity }

    fun cartTotal(): Long = cart.sumOf { it.subtotal }

    fun addToCart(product: Product, quantity: Int = 1): Boolean {
        val item = cart.firstOrNull { it.product.id == product.id }
        val current = item?.quantity ?: 0
        if (current + quantity > product.stock) return false
        if (item == null) cart.add(CartItem(product, quantity)) else item.quantity += quantity
        return true
    }

    fun updateQuantity(item: CartItem, quantity: Int): Boolean {
        if (quantity < 1 || quantity > item.product.stock) return false
        item.quantity = quantity
        return true
    }

    fun removeFromCart(item: CartItem) {
        cart.remove(item)
    }

    fun clearCart() = cart.clear()

    fun ordersOfBuyer(buyerId: Int): List<Order> = orders.filter { it.buyerId == buyerId }.sortedByDescending { it.id }

    fun ordersOfSeller(sellerId: Int): List<Order> =
        orders.filter { o -> o.lines.any { it.sellerId == sellerId } }.sortedByDescending { it.id }

    fun allOrders(): List<Order> = orders.sortedByDescending { it.id }

    fun order(id: String): Order? = orders.firstOrNull { it.id == id }

    fun checkout(buyer: User, address: String, method: String, result: PaymentResult): Order {
        val lines = cart.map {
            OrderLine(it.product.id, it.product.title, it.product.category, it.product.grade, it.product.price, it.quantity, it.product.sellerId)
        }
        val ref = "RS-PAY-" + (100000..999999).random()
        val status = when (result) {
            PaymentResult.APPROVED -> OrderStatus.PAID
            PaymentResult.PENDING -> OrderStatus.PENDING_PAYMENT
            PaymentResult.REJECTED -> OrderStatus.CANCELLED
        }
        val order = Order("RS-" + nextOrder++, buyer.id, lines, SHIPPING_COST, status, today(), address, method, ref)
        orders.add(order)
        if (result != PaymentResult.REJECTED) {
            cart.forEach { it.product.stock = (it.product.stock - it.quantity).coerceAtLeast(0) }
            cart.clear()
        }
        log(buyer.name, "Pago ${result.name.lowercase()}", "${order.id} · ${Format.money(order.total)} · $method", AuditType.PAYMENTS)
        return order
    }

    fun dispatchOrder(actor: User, order: Order, carrier: String, tracking: String) {
        order.carrier = carrier
        order.tracking = tracking
        order.status = OrderStatus.SHIPPED
        log(actor.name, "Despachó pedido", "${order.id} · $carrier guía $tracking", AuditType.ORDERS)
    }

    fun setOrderStatus(actor: User, order: Order, status: OrderStatus) {
        order.status = status
        log(actor.name, "Cambió estado de pedido", "${order.id} → ${status.label}", AuditType.ORDERS)
    }

    fun salesTotal(list: List<Order> = orders): Long =
        list.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.PENDING_PAYMENT }.sumOf { it.total }

    fun salesByCategory(): Map<Category, Long> {
        val map = linkedMapOf<Category, Long>()
        Category.values().forEach { map[it] = 0L }
        orders.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.PENDING_PAYMENT }
            .flatMap { it.lines }
            .forEach { map[it.category] = (map[it.category] ?: 0L) + it.subtotal }
        return map
    }

    fun topProducts(): List<Pair<String, Int>> =
        orders.filter { it.status != OrderStatus.CANCELLED }
            .flatMap { it.lines }
            .groupBy { it.title }
            .map { (title, lines) -> title to lines.sumOf { it.quantity } }
            .sortedByDescending { it.second }
            .take(5)

    fun auditLog(): List<AuditEvent> = audit.reversed()

    fun inPeriod(order: Order, days: Int): Boolean {
        val format = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO"))
        val date = try { format.parse(order.date) } catch (e: Exception) { null } ?: return false
        val today = format.parse(today()) ?: return false
        val diffDays = (today.time - date.time) / (24L * 60 * 60 * 1000)
        return diffDays in 0..days.toLong()
    }

    fun isSale(order: Order): Boolean =
        order.status != OrderStatus.CANCELLED && order.status != OrderStatus.PENDING_PAYMENT

    fun log(actor: String, action: String, detail: String, type: AuditType) {
        audit.add(AuditEvent(now(), actor, action, detail, type))
    }

    fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO")).format(Date())
    private fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CO")).format(Date())

    private fun seed() {
        users += User(1, "Laura Gómez Ruiz", "comprador@rsaldos.co", "Comprador123", "CC 1.020.456.789", "300 456 7812",
            Role.BUYER, UserStatus.ACTIVE, "Cra. 15 #93-40, apto 502", marketingConsent = true)
        users += User(2, "Tecnosaldos Bogotá", "vendedor@rsaldos.co", "Vendedor123", "NIT 901.234.567-1", "601 745 2210",
            Role.SELLER, UserStatus.ACTIVE, "Cl. 13 #8-20, local 104", storeName = "Tecnosaldos Bogotá")
        users += User(3, "María Rojas", "admin@rsaldos.co", "Admin123", "CC 52.789.441", "310 222 9087",
            Role.ADMIN, UserStatus.ACTIVE, "Oficina principal")
        users += User(4, "Andrés Pérez", "andres.p@correo.com", "Andres123", "CC 80.112.334", "315 778 1020",
            Role.BUYER, UserStatus.BLOCKED, "Cl. 80 #20-15")
        users += User(5, "Celulares S.A.S.", "ventas@celulares.co", "Celulares123", "NIT 900.555.120-4", "604 333 1200",
            Role.SELLER, UserStatus.PENDING, "Medellín", city = "Medellín", storeName = "Celulares S.A.S.")
        users += User(6, "Juan Martínez", "juan.m@correo.com", "Juan1234", "CC 1.032.998.120", "301 998 4521",
            Role.BUYER, UserStatus.ACTIVE, "Av. 68 #45-10")
        users += User(7, "Sofía Herrera", "sofia.h@correo.com", "Sofia1234", "CC 1.019.554.230", "312 554 2301",
            Role.BUYER, UserStatus.ACTIVE, "Cl. 140 #12-30")

        val s = 2
        products += Product(1, "Celular 128 GB, 8 GB RAM, grafito", "Samsung", Category.PHONES, Condition.REFURBISHED, Grade.A,
            689_900, 1_150_000, 4, "356789104512345", 6, "Ninguno conocido",
            "Batería 91 % · 32 pruebas superadas · borrado seguro certificado",
            "Equipo reacondicionado con pantalla original, cargador incluido y caja genérica.", s, ProductStatus.PUBLISHED, rating = 4.7)
        products += Product(2, "Portátil 14\" Core i5, 16 GB, SSD 512", "Lenovo", Category.LAPTOPS, Condition.REFURBISHED, Grade.B,
            1_349_000, 2_199_000, 3, "PF3XK19A", 6, "Rayón leve en la tapa (ver foto 4)",
            "Batería 88 % · teclado y puertos probados · Windows 11 limpio",
            "Portátil corporativo reacondicionado, ideal para estudio y oficina.", s, ProductStatus.PUBLISHED, rating = 4.5)
        products += Product(3, "Portátil 15.6\" Ryzen 7, 512 GB", "HP", Category.LAPTOPS, Condition.NEW_SURPLUS, Grade.A,
            1_899_000, 2_590_000, 2, "5CD2349XYZ", 12, "Ninguno",
            "Caja abierta · sin uso · 12 meses de garantía de fábrica",
            "Saldo de importación, equipo nuevo con caja abierta.", s, ProductStatus.PUBLISHED, rating = 4.9)
        products += Product(4, "Monitor 24\" Full HD IPS", "LG", Category.MONITORS, Condition.USED, Grade.A,
            389_000, 690_000, 6, "LG24MK-88123", 3, "Ninguno",
            "Sin píxeles muertos · cables HDMI incluidos",
            "Monitor usado proveniente de renovación corporativa.", s, ProductStatus.PUBLISHED, rating = 4.4)
        products += Product(5, "Portátil 13\" 8 GB (liquidación)", "Asus", Category.LAPTOPS, Condition.LIQUIDATION, Grade.C,
            799_000, 1_499_000, 1, "ASX13-77812", 3, "Marcas visibles en carcasa y borde inferior",
            "Funcional al 100 % · batería 79 %",
            "Unidad de liquidación con defectos estéticos declarados.", s, ProductStatus.PUBLISHED, rating = 4.1)
        products += Product(6, "Tableta 10\" 64 GB Wi-Fi", "Samsung", Category.TABLETS, Condition.REFURBISHED, Grade.B,
            529_000, 849_000, 5, "R52T10-99821", 6, "Micro rayones en la pantalla",
            "Batería 90 % · cámara y Wi-Fi probados",
            "Tableta reacondicionada para estudio y entretenimiento.", s, ProductStatus.PUBLISHED, rating = 4.3)
        products += Product(7, "Audífonos inalámbricos ANC", "Sony", Category.AUDIO, Condition.NEW_SURPLUS, Grade.A,
            459_000, 799_000, 8, "SNYWH-55410", 12, "Ninguno",
            "Producto nuevo de saldo · empaque original",
            "Cancelación activa de ruido y 30 h de batería.", s, ProductStatus.PUBLISHED, rating = 4.8)
        products += Product(8, "Reloj inteligente 44 mm", "Xiaomi", Category.WEARABLES, Condition.USED, Grade.B,
            219_000, 399_000, 2, "XMW44-12093", 3, "Correa con desgaste",
            "Sensores y batería probados",
            "Reloj usado con cargador original.", s, ProductStatus.PUBLISHED, rating = 4.2)
        products += Product(9, "Celular 256 GB 5G, azul", "Motorola", Category.PHONES, Condition.LIQUIDATION, Grade.B,
            899_000, 1_590_000, 1, "359112045698771", 6, "Marco con golpe leve",
            "Batería 93 % · 28 pruebas superadas",
            "Unidad de lote de liquidación.", s, ProductStatus.REVIEW, rating = 4.5)
        products += Product(10, "Tableta 8\" 32 GB", "Lenovo", Category.TABLETS, Condition.USED, Grade.C,
            249_000, 459_000, 3, "LNVT8-00981", 3, "Pantalla con rayones visibles",
            "Funcional · batería 80 %", "Tableta básica para niños.", 5, ProductStatus.REVIEW, rating = 4.0)

        cart += CartItem(products[0], 1)
        cart += CartItem(products[6], 1)

        fun line(p: Product, q: Int) = OrderLine(p.id, p.title, p.category, p.grade, p.price, q, p.sellerId)
        orders += Order("RS-2041", 1, listOf(line(products[3], 1)), SHIPPING_COST, OrderStatus.DELIVERED, "28/09/2026",
            "Cra. 15 #93-40, Bogotá", "Tarjeta ****4242", "RS-PAY-551203", "Servientrega", "SV-77812033")
        orders += Order("RS-2044", 6, listOf(line(products[1], 1)), SHIPPING_COST, OrderStatus.DELIVERED, "02/10/2026",
            "Av. 68 #45-10, Bogotá", "PSE Bancolombia", "RS-PAY-551877", "Coordinadora", "CO-99120345")
        orders += Order("RS-2046", 7, listOf(line(products[6], 2)), SHIPPING_COST, OrderStatus.SHIPPED, "08/10/2026",
            "Cl. 140 #12-30, Bogotá", "Nequi", "RS-PAY-552301", "Interrapidísimo", "IR-55120987")
        orders += Order("RS-2048", 1, listOf(line(products[0], 1), line(products[7], 1)), SHIPPING_COST, OrderStatus.PREPARING,
            "15/10/2026", "Cra. 15 #93-40, Bogotá", "Tarjeta ****4242", "RS-PAY-552990")
        orders += Order("RS-2049", 6, listOf(line(products[2], 1)), SHIPPING_COST, OrderStatus.PAID, "21/10/2026",
            "Av. 68 #45-10, Bogotá", "Tarjeta ****1881", "RS-PAY-553120")
        orders += Order("RS-2050", 7, listOf(line(products[5], 1)), SHIPPING_COST, OrderStatus.PAID, "26/10/2026",
            "Cl. 140 #12-30, Bogotá", "PSE Davivienda", "RS-PAY-553402")

        audit += AuditEvent("27/10/2026 22:14", "Sistema", "Webhook rechazado", "Firma inválida · 3 intentos", AuditType.SECURITY)
        audit += AuditEvent("27/10/2026 23:02", "Sistema", "Cuenta bloqueada", "andres.p@correo.com · 5 intentos fallidos", AuditType.SECURITY)
        audit += AuditEvent("28/10/2026 08:30", "Tecnosaldos Bogotá", "Creó producto", "Celular 256 GB 5G, azul", AuditType.CATALOG)
        audit += AuditEvent("28/10/2026 09:12", "María Rojas", "Conciliación", "RS-2049 aprobado por pasarela", AuditType.PAYMENTS)
        audit += AuditEvent("28/10/2026 10:45", "Celulares S.A.S.", "Registro de cuenta", "Rol Vendedor · Pendiente", AuditType.USERS)
    }
}
