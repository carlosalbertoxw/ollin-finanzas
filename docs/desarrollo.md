# Desarrollo

## Entorno

| Pieza | Versión |
|---|---|
| Gradle wrapper | 8.14.5 |
| Android Gradle Plugin | 8.10.0 |
| Kotlin / KSP | 2.1.20 / 2.1.20-2.0.1 |
| Compose BOM | 2025.04.01 |
| Room | 2.7.1 |
| SQLCipher | 4.6.1 |
| JDK del proyecto | 17 (`sourceCompatibility`, `jvmTarget`) |
| JDK para correr Gradle | **17 a 21** |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

### El JDK de Gradle

Kotlin 2.1.20 no arranca con JDK 25 ni 26: su compilador no sabe leer esas versiones y la compilación muere con un mensaje que es solo el número de versión, sin más pista:

```
* What went wrong:
26.0.1
```

Ojo con el JBR que trae Android Studio: en instalaciones recientes ya es 25 y falla igual. Hay que apuntar `JAVA_HOME` a un JDK 21 — Android Studio suele dejar uno en `~/.jdks/`.

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew tasks
```

Desde Android Studio, el ajuste equivalente es *Gradle JDK*.

`local.properties` (no versionado) debe apuntar al SDK de Android:

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

## Comandos

```bash
./gradlew :app:assembleDebug      # APK de depuración
./gradlew :app:installDebug       # instala en el dispositivo conectado
./gradlew :app:testDebugUnitTest  # pruebas unitarias (JVM)
./gradlew :app:assembleRelease    # con minify y shrink de recursos
./gradlew clean
```

El APK queda en `app/build/outputs/apk/debug/`. La variante `debug` lleva `applicationIdSuffix = ".debug"` y `versionNameSuffix = "-debug"`, así que convive con la de producción instalada.

## Configuración notable del build

- **`resourceConfigurations += listOf("es")`** — la app está escrita en español; no se empaquetan los recursos de las bibliotecas en los otros ochenta idiomas.
- **`room.schemaLocation`** — KSP escribe los esquemas en `app/schemas/`, que sí se versionan: son la referencia contra la que se prueban las migraciones.
- **`androidx.fragment` fijado a mano** — `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`: su `FragmentActivity` rechaza los request codes de más de 16 bits que genera `activity` 1.10.1, y **cualquier** selector de archivos revienta al abrirse. Quitar esa línea de `libs.versions.toml` vuelve a romper importar y exportar.
- **`testOptions.unitTests`** con `isIncludeAndroidResources` y `isReturnDefaultValues`, para que las pruebas en la JVM no tropiecen con las clases stub de Android.
- **`release`** con `isMinifyEnabled` e `isShrinkResources`.

La pantalla de Acerca de lee la versión del **paquete instalado** (`PackageManager`) y no de `BuildConfig`: así muestra la que de verdad está corriendo en el teléfono.

## Pruebas

Corren en la JVM, sin emulador ni dispositivo.

| Prueba | Qué cubre |
|---|---|
| [`ExcelRoundTripTest`](../app/src/test/java/mx/ollin/finanzas/ExcelRoundTripTest.kt) | Serial de fechas, letras de columna, centavos sin error acumulado, escritura y relectura del libro en ambos esquemas, escapado de XML, exportación parcial y libro vacío |
| [`ExportadorBordesTest`](../app/src/test/java/mx/ollin/finanzas/ExportadorBordesTest.kt) | Compromisos con datos, catálogos incompletos, nombres que obligan a entrecomillar, y tres años de movimientos diarios |

Las pruebas de Excel escriben libros reales en `app/build/pruebas/`, útiles para abrirlos a mano y comprobar el resultado. El reporte HTML queda en `app/build/reports/tests/`.

Robolectric está declarado como dependencia de prueba para lo que necesite `Context`. Cualquier prueba que abra la base tiene que arrancar con una `Application` pelona en vez de `OllinApp`: la de verdad siembra el catálogo contra la base cifrada, y SQLCipher es una biblioteca nativa de Android que en la JVM no existe.

## Convenciones del código

- **Todo en español**: nombres de clases, funciones, variables y comentarios. Los nombres de prueba van en backticks y en prosa (`` `el modo compacto emite exactamente ocho columnas` ``).
- **Los comentarios explican el porqué, no el qué.** Si una decisión tiene una alternativa obvia que se descartó, el comentario dice por qué se descartó.
- **Sin acentos en los comentarios y literales del código** (la documentación de `docs/` sí los usa).
- **Una pantalla por archivo**, con su ViewModel arriba y los composables privados abajo.
- **La escritura pasa por el repositorio.** Las pantallas no tocan los DAO.
- **Los importes son centavos en `Long`**, nunca decimales flotantes.
- Las cadenas visibles están en el código, no en `strings.xml`: la app es monolingüe por diseño. En `strings.xml` solo viven el nombre, el lema y los textos del canal de notificaciones.

## Añadir una pantalla

1. Crea el archivo en `ui/screens/` con su `ViewModel` y su composable.
2. Declara la ruta en [`ui/nav/Destinos.kt`](../app/src/main/java/mx/ollin/finanzas/ui/nav/Destinos.kt) (`Destino` si es pestaña, `Rutas` si cuelga de una).
3. Regístrala en el `NavHost` de [`ui/OllinRaiz.kt`](../app/src/main/java/mx/ollin/finanzas/ui/OllinRaiz.kt).
4. Si la ruta lleva argumentos, agrégalos con `navArgument` y su `defaultValue`; el patrón de la app es usar `0L` como "sin valor" y filtrarlo con `takeIf { it > 0L }`.

## Añadir un tutorial

Agrega una entrada a `TUTORIALES` en [`ui/screens/TutorialesPantalla.kt`](../app/src/main/java/mx/ollin/finanzas/ui/screens/TutorialesPantalla.kt), con su clave, resumen, pasos y, si lo hay, el truco: lo que casi nadie descubre solo y evita el error más común del tema.

Los pasos son texto plano y no capturas de pantalla a propósito: una captura envejece con el primer cambio de la interfaz y después miente, mientras que el orden de los pasos sigue siendo cierto.

## Añadir una versión de la base

Ver el final de [modelo de datos](modelo-de-datos.md#migraciones).
