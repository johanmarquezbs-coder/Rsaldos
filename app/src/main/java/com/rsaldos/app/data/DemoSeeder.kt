package com.rsaldos.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.rsaldos.app.R
import kotlinx.coroutines.tasks.await

/**
 * Cuentas y catálogo de demostración.
 *
 * La primera vez que alguien entra con una de las cuentas de prueba (comprador, vendedor o
 * administrador) y la cuenta aún no existe en Firebase, se crea en Firebase Authentication y
 * se guarda su perfil en Firestore. Al crear la cuenta del vendedor se carga además un
 * catálogo inicial de productos, para que la app tenga datos desde el primer uso.
 */
object DemoSeeder {

    /** Datos de una cuenta de demostración. */
    private data class Demo(
        val email: String, val password: String, val role: Role, val name: String,
        val document: String, val phone: String, val address: String, val storeName: String = ""
    )

    private val accounts = listOf(
        Demo("comprador@rsaldos.co", "Comprador123", Role.BUYER, "Laura Gómez Ruiz", "CC 1.020.456.789",
            "300 456 7812", "Cra. 15 #93-40, apto 502"),
        Demo("vendedor@rsaldos.co", "Vendedor123", Role.SELLER, "Tecnosaldos Bogotá", "NIT 901.234.567-1",
            "601 745 2210", "Cl. 13 #8-20, local 104", "Tecnosaldos Bogotá"),
        Demo("admin@rsaldos.co", "Admin123", Role.ADMIN, "María Rojas", "CC 52.789.441",
            "310 222 9087", "Oficina principal")
    )

    /** Indica si el correo y la contraseña corresponden a una cuenta de demostración. */
    fun isDemo(email: String, password: String): Boolean =
        accounts.any { it.email == email.trim().lowercase() && it.password == password }

    /** Construye el perfil de una cuenta de demostración o null si el correo no es de demo. */
    fun profileIfDemo(uid: String, email: String): User? {
        val demo = accounts.firstOrNull { it.email == email.trim().lowercase() } ?: return null
        return User(
            id = uid, name = demo.name, email = demo.email, document = demo.document, phone = demo.phone,
            role = demo.role, status = UserStatus.ACTIVE, address = demo.address, storeName = demo.storeName,
            marketingConsent = demo.role == Role.BUYER, createdAt = Repository.today()
        )
    }

    /**
     * Crea la cuenta de demostración en Firebase Authentication (queda con la sesión iniciada),
     * guarda su perfil y, si es el vendedor, carga el catálogo inicial. Devuelve el UID.
     */
    suspend fun createDemoAccount(email: String): String {
        val demo = accounts.first { it.email == email }
        val uid = try {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(demo.email, demo.password).await().user?.uid
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AppException(R.string.error_credentials, e)
        } ?: throw AppException(R.string.error_generic)
        val profile = profileIfDemo(uid, demo.email) ?: throw AppException(R.string.error_generic)
        Repository.saveNewProfile(profile)
        if (demo.role == Role.SELLER) seedProducts(profile)
        Repository.log(profile.name, "Cuenta de demostración creada", "Rol ${profile.role.label}", AuditType.USERS)
        return uid
    }

    /** Escribe en Firestore el catálogo inicial del vendedor de demostración. */
    private suspend fun seedProducts(seller: User) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        demoProducts(seller).forEach { product ->
            batch.set(db.collection(Repository.PRODUCTS).document(product.id), product.toMap())
        }
        batch.commit().await()
    }

    /** Productos de ejemplo (equipos reacondicionados, usados, de saldo y de liquidación). */
    private fun demoProducts(s: User): List<Product> {
        fun p(
            n: Int, title: String, brand: String, category: Category, condition: Condition, grade: Grade,
            price: Long, oldPrice: Long, stock: Int, serial: String, warranty: Int, defects: String,
            tests: String, description: String, status: ProductStatus = ProductStatus.PUBLISHED, rating: Double
        ) = Product(
            id = "${s.id.take(6)}-demo-%02d".format(n), title = title, brand = brand, category = category,
            condition = condition, grade = grade, price = price, oldPrice = oldPrice, stock = stock,
            serial = serial, warrantyMonths = warranty, defects = defects, tests = tests,
            description = description, sellerId = s.id, status = status, sellerName = s.storeName, rating = rating
        )
        return listOf(
            p(1, "Celular 128 GB, 8 GB RAM, grafito", "Samsung", Category.PHONES, Condition.REFURBISHED, Grade.A,
                689_900, 1_150_000, 4, "356789104512345", 6, "Ninguno conocido",
                "Batería 91 % · 32 pruebas superadas · borrado seguro certificado",
                "Equipo reacondicionado con pantalla original, cargador incluido y caja genérica.", rating = 4.7),
            p(2, "Portátil 14\" Core i5, 16 GB, SSD 512", "Lenovo", Category.LAPTOPS, Condition.REFURBISHED, Grade.B,
                1_349_000, 2_199_000, 3, "PF3XK19A", 6, "Rayón leve en la tapa",
                "Batería 88 % · teclado y puertos probados · Windows 11 limpio",
                "Portátil corporativo reacondicionado, ideal para estudio y oficina.", rating = 4.5),
            p(3, "Portátil 15.6\" Ryzen 7, 512 GB", "HP", Category.LAPTOPS, Condition.NEW_SURPLUS, Grade.A,
                1_899_000, 2_590_000, 2, "5CD2349XYZ", 12, "Ninguno",
                "Caja abierta · sin uso · 12 meses de garantía de fábrica",
                "Saldo de importación, equipo nuevo con caja abierta.", rating = 4.9),
            p(4, "Monitor 24\" Full HD IPS", "LG", Category.MONITORS, Condition.USED, Grade.A,
                389_000, 690_000, 6, "LG24MK-88123", 3, "Ninguno",
                "Sin píxeles muertos · cables HDMI incluidos",
                "Monitor usado proveniente de renovación corporativa.", rating = 4.4),
            p(5, "Portátil 13\" 8 GB (liquidación)", "Asus", Category.LAPTOPS, Condition.LIQUIDATION, Grade.C,
                799_000, 1_499_000, 1, "ASX13-77812", 3, "Marcas visibles en carcasa y borde inferior",
                "Funcional al 100 % · batería 79 %",
                "Unidad de liquidación con defectos estéticos declarados.", rating = 4.1),
            p(6, "Tableta 10\" 64 GB Wi-Fi", "Samsung", Category.TABLETS, Condition.REFURBISHED, Grade.B,
                529_000, 849_000, 5, "R52T10-99821", 6, "Micro rayones en la pantalla",
                "Batería 90 % · cámara y Wi-Fi probados",
                "Tableta reacondicionada para estudio y entretenimiento.", rating = 4.3),
            p(7, "Audífonos inalámbricos ANC", "Sony", Category.AUDIO, Condition.NEW_SURPLUS, Grade.A,
                459_000, 799_000, 8, "SNYWH-55410", 12, "Ninguno",
                "Producto nuevo de saldo · empaque original",
                "Cancelación activa de ruido y 30 h de batería.", rating = 4.8),
            p(8, "Reloj inteligente 44 mm", "Xiaomi", Category.WEARABLES, Condition.USED, Grade.B,
                219_000, 399_000, 2, "XMW44-12093", 3, "Correa con desgaste",
                "Sensores y batería probados", "Reloj usado con cargador original.", rating = 4.2),
            p(9, "Celular 256 GB 5G, azul", "Motorola", Category.PHONES, Condition.LIQUIDATION, Grade.B,
                899_000, 1_590_000, 1, "359112045698771", 6, "Marco con golpe leve",
                "Batería 93 % · 28 pruebas superadas", "Unidad de lote de liquidación.",
                ProductStatus.REVIEW, rating = 4.5),
            p(10, "Tableta 8\" 32 GB", "Lenovo", Category.TABLETS, Condition.USED, Grade.C,
                249_000, 459_000, 3, "LNVT8-00981", 3, "Pantalla con rayones visibles",
                "Funcional · batería 80 %", "Tableta básica para niños.", ProductStatus.REVIEW, rating = 4.0)
        )
    }
}
