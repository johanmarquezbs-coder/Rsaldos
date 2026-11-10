# R-Saldos

Tienda virtual Android de tecnología reacondicionada y de liquidación: celulares, computadores, tabletas y accesorios con condición, grado estético, garantía y defectos visibles antes de pagar.

Proyecto del curso **Desarrollo de Aplicaciones Móviles Nativas** — Ingeniería de Software.
Autor: Johan Sebastián Márquez Bonilla.

## Tecnologías

- Kotlin 1.9.24 y Android SDK 34 (mínimo API 26)
- Layouts XML, Material Components 3, ConstraintLayout, RecyclerView y ViewBinding
- DrawerLayout + BottomNavigationView por rol
- Biometría con `androidx.biometric` (BiometricPrompt)
- Gradle 8.7 con Android Gradle Plugin 8.5.2
- Docker para compilar el APK sin instalar el SDK

## Pantallas por rol

| Rol | Pantallas |
|---|---|
| Todos | Splash, inicio de sesión con huella, registro, recuperación de contraseña, menú lateral y barra inferior |
| Comprador | Inicio, catálogo con filtros, detalle de producto, carrito, entrega y pasarela de pagos, resultado del pago, mis pedidos y perfil |
| Vendedor | Panel, CRUD de productos con cámara, pedidos recibidos con despacho y perfil de vendedor |
| Administrador | Dashboard, gestión de usuarios (listado, creación, edición y eliminación), gestión de productos, pagos y conciliación, reporte de ventas y auditoría |

## Cuentas de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Comprador | comprador@rsaldos.co | Comprador123 |
| Vendedor | vendedor@rsaldos.co | Vendedor123 |
| Administrador | admin@rsaldos.co | Admin123 (código MFA 246810) |

- Código de recuperación de contraseña: `135790`
- Tarjeta aprobada: `4242 4242 4242 4242` · tarjeta rechazada: `4000 0000 0000 0002`

Los datos son ficticios y viven en memoria; se reinician al cerrar la app.

## Cómo ejecutarlo

**Android Studio:** abrir la carpeta del proyecto, esperar la sincronización de Gradle y ejecutar en un emulador o celular.

**Línea de comandos:**

```bash
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

**Docker** (no necesita el SDK instalado):

```bash
docker compose up -d --build
```

Abre http://localhost:8095 para descargar el APK. Para sacar solo el archivo:

```bash
docker build --target export --output dist .
```

**Compilar y abrir en el emulador (Windows, sin Android Studio):**

```powershell
powershell -ExecutionPolicy Bypass -File .\emulador.ps1
```

El script hace todo en orden:

1. Descarga el JDK 17 y el SDK de Android en `%LOCALAPPDATA%\Android` si no están instalados.
2. Compila la app con `gradlew assembleDebug`.
3. Crea el dispositivo `RSaldos` (Pixel 6, Android 14) si no existe.
4. Abre el emulador, instala el APK y abre R-Saldos.

La primera vez tarda varios minutos por las descargas. Para solo compilar el APK:

```powershell
powershell -ExecutionPolicy Bypass -File .\emulador.ps1 -SoloCompilar
```

Si el emulador no abre:

- Verificar la aceleración: `& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator-check.exe" accel` debe decir que WHPX está disponible. Si no, activar "Plataforma del hipervisor de Windows" en Características de Windows y reiniciar.
- Si aparece "System UI isn't responding", tocar **Wait**.

## Estructura

```
app/src/main/
├── java/com/rsaldos/app/
│   ├── data/        modelos, repositorio en memoria y sesión
│   └── ui/
│       ├── auth/    splash, login, registro y recuperación
│       ├── buyer/   pantallas del comprador
│       ├── seller/  pantallas del vendedor
│       ├── admin/   pantallas del administrador
│       └── common/  navegación por rol, perfil, biometría y adaptadores
└── res/
    ├── layout/      activities, fragments, ítems y diálogos
    ├── values/      colors, dimens, strings, styles y themes
    ├── drawable/    íconos vectoriales y fondos
    ├── color/       selectores de color
    └── menu/        menús Drawer y Bottom Navigation por rol
```

## Ramas

- `main`: versión entregable
- `dev`: desarrollo
