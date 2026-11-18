# R-Saldos

Tienda virtual Android de tecnología reacondicionada y de liquidación: celulares, computadores, tabletas y accesorios con condición, grado estético, garantía y defectos visibles antes de pagar.

Proyecto final del curso **Desarrollo de Aplicaciones Móviles Nativas** — Ingeniería de Software.
Autor: Johan Sebastián Márquez Bonilla.

## Qué hace (versión 2.0)

| Componente | Implementación |
|---|---|
| Autenticación | Firebase Authentication: registro, inicio y cierre de sesión, recuperación por correo y cambio de contraseña para Comprador, Vendedor y Administrador |
| Huella dactilar | `BiometricPrompt` + credenciales cifradas con Android Keystore (`EncryptedSharedPreferences`) |
| CRUD de usuarios | Administrador: crear (sin perder su sesión), editar, aprobar, bloquear y eliminar (lógico) en Firestore |
| CRUD de productos | Vendedor y Administrador; fotos desde cámara o galería, comprimidas y guardadas en Firestore |
| Carrito y órdenes | `users/{uid}/cart` y `orders` en Firestore, con historial y estados de despacho |
| Geolocalización | Fused Location Provider + Geocoder; coordenadas guardadas en el pedido y visibles en Google Maps |
| Pasarela de pagos | Checkout de ePayco en WebView + validación de la transacción por `ref_payco` |

## Tecnologías

- Kotlin 1.9.24, Android SDK 34 (mínimo API 26), Gradle 8.7, AGP 8.5.2
- Layouts XML, Material Components 3, ViewBinding, RecyclerView, DrawerLayout + BottomNavigationView
- Firebase BoM 33.1.2 (Auth y Firestore), Kotlin Coroutines
- Google Play Services Location 21.3.0, Coil 2.6 (imágenes)
- androidx.biometric 1.1.0, androidx.security-crypto 1.0.0
- ePayco Checkout (checkout.js) y API de validación
- Docker para compilar el APK sin instalar el SDK

## Configuración

### 1. Firebase

1. Crear un proyecto en <https://console.firebase.google.com> y registrar una app Android con el paquete `com.rsaldos.app`.
2. Descargar `google-services.json` en la carpeta `app/`.
3. **Authentication** → Método de acceso → activar *Correo electrónico/contraseña* (Plantillas → idioma español).
4. **Firestore Database** → crear la base de datos y publicar las reglas de `firestore.rules`.

Con Firebase CLI: `firebase deploy --only firestore:rules`.

La app funciona con el plan gratuito **Spark**: como Firebase Storage exige el plan de pago Blaze, las fotos de producto se guardan comprimidas (JPEG, máximo 960 px y 450 KB) en la colección `productPhotos` de Firestore y se muestran con un fetcher propio de Coil.

El proyecto ya está conectado a Firebase (`r-saldos`) con `app/google-services.json`.

Sin `google-services.json` el proyecto compila igual, pero la app muestra al abrir cómo configurarlo.

### 2. ePayco

En `gradle.properties`:

```properties
EPAYCO_PUBLIC_KEY=<PUBLIC_KEY del comercio>   # Dashboard ePayco > Configuración > Llaves secretas
EPAYCO_TEST=true                              # entorno de pruebas, no cobra dinero real
```

Por defecto trae la llave pública de demostración que publica ePayco en su documentación.

## Cuentas de prueba

Se crean solas la primera vez que se usan (y el vendedor carga un catálogo de 10 productos):

| Rol | Correo | Contraseña |
|---|---|---|
| Comprador | comprador@rsaldos.co | Comprador123 |
| Vendedor | vendedor@rsaldos.co | Vendedor123 |
| Administrador | admin@rsaldos.co | Admin123 (código de verificación 246810) |

## Cómo compilar

| Opción | Comando | APK |
|---|---|---|
| Android Studio | Abrir la carpeta, sincronizar Gradle y ejecutar | Se instala en el emulador o celular |
| Línea de comandos | `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) | `app/build/outputs/apk/debug/app-debug.apk` |
| Docker | `docker build --target export --output dist .` (o doble clic en `compilar_rsaldos.bat`) | `dist/rsaldos-debug.apk` |

## Estructura

```
app/src/main/
├── java/com/rsaldos/app/
│   ├── RSaldosApp.kt      inicialización de Firebase
│   ├── data/              modelos, Repository (Firestore), AuthService, CredentialStore,
│   │                      StorageService, LocationService, EpaycoService, DemoSeeder
│   └── ui/
│       ├── auth/          splash, login con huella, registro y recuperación
│       ├── buyer/         inicio, catálogo, detalle, carrito, checkout, ePayco, pedidos
│       ├── seller/        panel, productos con cámara/galería, pedidos y despachos
│       ├── admin/         dashboard, usuarios, productos, pagos, ventas y auditoría
│       └── common/        navegación por rol, perfil, biometría, adaptadores y utilidades
└── res/                   layouts, valores, íconos y menús por rol
firestore.rules            reglas de seguridad de Firestore
```

Cada clase y método relevante tiene comentarios KDoc que explican su propósito y su lógica.

## Ramas

- `main`: versión entregable
- `dev`: desarrollo
