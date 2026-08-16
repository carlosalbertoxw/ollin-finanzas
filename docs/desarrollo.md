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
./gradlew :app:connectedDebugAndroidTest  # pruebas de interfaz (necesita dispositivo)
./gradlew :app:assembleRelease    # con minify y shrink de recursos
./gradlew clean
```

El APK queda en `app/build/outputs/apk/debug/`. La variante `debug` lleva `applicationIdSuffix = ".debug"` y `versionNameSuffix = "-debug"`, así que convive con la de producción instalada.

## APK de producción

Android no instala un APK sin firmar, así que `assembleRelease` necesita una llave. La configuración de firma se lee de `keystore.properties`, en la raíz del proyecto y **fuera del control de versiones**.

### 1. Crear el almacén (una sola vez)

```bash
keytool -genkeypair -v -keystore ollin-finanzas-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias ollin-finanzas
```

`keytool` viene con el JDK, pero no suele estar en el `PATH`: llámalo por ruta completa (`$HOME/.jdks/jbr-21.0.11/bin/keytool`) o usa *Build → Generate Signed App Bundle / APK → Create new…* en Android Studio, que es el mismo programa con formulario. Pide una contraseña y los datos del certificado; solo el *nombre y apellidos* (CN) aparece luego en la firma.

El alias lleva el nombre de la app y no solo `ollin` porque hay otras apps de la familia (Ollin Actividades). El alias solo tiene que ser único **dentro de un mismo archivo**, así que dos keystores separados podrían usar el mismo sin romperse; el motivo es no confundirte al abrirlo con `keytool -list` dentro de unos años, y dejar la puerta abierta a guardar ambas llaves en un solo almacén.

Los 10 000 días (~27 años) no son capricho: Google Play exige un certificado válido más allá de 2033, y una llave caducada deja la app sin poder actualizarse.

> **El archivo es irremplazable.** Android identifica al autor de la app por esta llave: un APK firmado distinto no se instala encima del anterior, obliga a desinstalar y con ello borra la base de datos del usuario. Nadie —tampoco Google— puede regenerarla. Guarda una copia fuera de la máquina.

### 2. Declarar dónde está

```bash
cp keystore.properties.example keystore.properties
```

Y rellena los cuatro valores:

```properties
storeFile=ollin-release.jks
storePassword=<la del almacén>
keyAlias=ollin
keyPassword=<la de la llave>
```

`storeFile` es relativa a la raíz del proyecto; si guardas el `.jks` fuera del repositorio —lo recomendable—, pon la ruta absoluta.

### 3. Construir

```bash
./gradlew :app:assembleRelease   # APK  -> app/build/outputs/apk/release/
./gradlew :app:bundleRelease     # AAB  -> app/build/outputs/bundle/release/  (solo para Play)
```

El APK sale ya firmado y listo para instalar con `adb install` o pasándolo al teléfono.

### Cuando falta la configuración

Sin `keystore.properties` el build **no falla**: el `signingConfig` queda nulo, el APK sale sin firmar y Gradle avisa con un `warning`. Es a propósito, para que el proyecto recién clonado siga compilando y pasando pruebas sin exigir una llave.

Si el archivo existe pero está incompleto, o apunta a un `.jks` inexistente, ahí sí falla de inmediato y dice cuál es el problema: a esas alturas la intención de firmar ya es evidente y un APK sin firma sería una sorpresa peor.

## Configuración notable del build

- **`resourceConfigurations += listOf("es")`** — la app está escrita en español; no se empaquetan los recursos de las bibliotecas en los otros ochenta idiomas.
- **`room.schemaLocation`** — KSP escribe los esquemas en `app/schemas/`, que sí se versionan: son la referencia contra la que se prueban las migraciones.
- **`androidx.fragment` fijado a mano** — `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`: su `FragmentActivity` rechaza los request codes de más de 16 bits que genera `activity` 1.10.1, y **cualquier** selector de archivos revienta al abrirse. Quitar esa línea de `libs.versions.toml` vuelve a romper importar y exportar.
- **`testOptions.unitTests`** con `isIncludeAndroidResources` y `isReturnDefaultValues`, para que las pruebas en la JVM no tropiecen con las clases stub de Android.
- **`release`** con `isMinifyEnabled` e `isShrinkResources`.

La pantalla de Acerca de lee la versión del **paquete instalado** (`PackageManager`) y no de `BuildConfig`: así muestra la que de verdad está corriendo en el teléfono.

## Pruebas

Hay dos suites: **94 pruebas unitarias** en la JVM y **10 pruebas de interfaz** que necesitan dispositivo.

### Unitarias (JVM)

```bash
./gradlew :app:testDebugUnitTest
```

| Prueba | Qué cubre |
|---|---|
| [`DineroTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/DineroTest.kt) | `parsea` con coma de millares contra coma decimal, formato europeo, paréntesis contables y basura; redondeo HALF_UP; los tres tramos de `formateaCompacto` con signo |
| [`EnumsYNormalizacionTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/EnumsYNormalizacionTest.kt) | `normalizaClave` (acentos, espacios, idempotencia) y el `desdeEtiqueta` de cada enum; el signo esperado de los seis tipos de movimiento |
| [`ProyeccionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ProyeccionesTest.kt) | Las guardas de división entre cero de `tasaAhorro` y `avance`, y que el presupuesto use el absoluto del gasto |
| [`ClavePinTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ClavePinTest.kt) | PIN correcto e incorrecto, hash o sal ausentes, Base64 corrupto, y que la derivación sea determinista por sal |
| [`RevisaCalidadTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/RevisaCalidadTest.kt) | Los nueve detectores, uno por prueba, más el libro sano que no debe reportar nada. Afirma sobre los **datos** del hallazgo (cuentas citadas, periodos, importe), no sobre su prosa |
| [`ReparaDatosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ReparaDatosTest.kt) | Las tres reparaciones automáticas, y sobre todo que **ninguna toque el importe** |
| [`FinanzasRepositorioTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/FinanzasRepositorioTest.kt) | Los invariantes de la puerta única de escritura: una transferencia nace con sus dos patas y muere con las dos, el origen no puede ser el destino, la contraparte se deriva aunque le manden otra, y el plan de un compromiso avanza y se deshace sin perder el día del mes |
| [`ControlBloqueoTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ControlBloqueoTest.kt) | Arrancar cerrado, la gracia del selector de archivos con reloj monótono, y el freno contra la fuerza bruta: escalada de la espera, tope y persistencia del contador |
| [`RecordatoriosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/RecordatoriosTest.kt) | Qué avisa y qué no: la ventana, lo vencido primero, planes apagados o ya terminados, y la fecha formateada para una persona |
| [`ImportadorExcelTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorExcelTest.kt) | Round trip exportar→importar, sinónimos de encabezado, renglones incompletos, emparejado de transferencias e inferencia de tipo de cuenta |
| [`ImportadorHojasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorHojasTest.kt) | El regreso de Diccionarios, Presupuesto y Compromisos: naturaleza declarada, jerarquía de categorías, metas por mes, compromisos reconstruidos desde el próximo pago y el libro sin hoja de movimientos |
| [`ExcelRoundTripTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExcelRoundTripTest.kt) | Serial de fechas, letras de columna, centavos sin error acumulado, escritura y relectura del libro en ambos esquemas, escapado de XML, exportación parcial y libro vacío |
| [`ExportadorBordesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExportadorBordesTest.kt) | Compromisos con datos, catálogos incompletos, nombres que obligan a entrecomillar, y tres años de movimientos diarios |

Las pruebas de Excel escriben libros reales en `app/build/pruebas/`, útiles para abrirlos a mano y comprobar el resultado. El reporte HTML queda en `app/build/reports/tests/`.

### La base en memoria

Las suites de calidad, reparación e importación heredan de [`BaseEnMemoria`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/BaseEnMemoria.kt), que abre `OllinDatabase` con `Room.inMemoryDatabaseBuilder` en vez de `OllinDatabase.obten`. Eso **se salta SQLCipher a propósito**: es una biblioteca nativa de Android y en la JVM no existe. Lo que se prueba ahí es el SQL y la lógica que cuelga de él; el cifrado solo se puede verificar en dispositivo.

Dos detalles que se rompen solos si se tocan:

- **`@Config(application = Application::class)`** — con `OllinApp` la prueba sembraría el catálogo y programaría notificaciones antes de empezar.
- **`sdk = [34]`** — Robolectric 4.14.1 no trae imagen para la 36 del `targetSdk`, y sin fijarlo cada prueba muere al arrancar. Al subir Robolectric, sube también este número.

Las claves foráneas están activas, así que un movimiento necesita una cuenta que exista de verdad; para eso están los ayudantes `nuevaCuenta` / `nuevoMovimiento`.

### Lo que no necesita base

`ControlBloqueoTest` y `RecordatoriosTest` corren sin Robolectric y sin DataStore, porque las clases que prueban reciben lo que usan en vez de un repositorio entero: `ControlBloqueo` toma un `Flow<Ajustes>`, la función que guarda los fallos y un reloj; `Recordatorios.porVencer` es una función pura sobre fechas.

Dos trampas si tocas `ControlBloqueoTest`:

- El control colecciona las preferencias **para siempre**, así que su scope no puede ser el de `runTest`: la prueba esperaría por siempre a ese hijo. Va en un `CoroutineScope` propio que se cancela en `@After`.
- Ese scope usa `UnconfinedTestDispatcher`, así el `collect` corre al construirse y cada prueba arranca con las preferencias ya leídas. La prueba de "arranca bloqueado" es la excepción: recibe un flujo que todavía no emite, porque es justo el instante que quiere retratar.

### De interfaz (dispositivo)

```bash
./gradlew :app:connectedDebugAndroidTest
```

Requieren un emulador o teléfono con **API 26 o superior**; no corren en la JVM. Lo que se instala es `com.carlosalbertoxw.ollin.finanzas.debug`.

| Prueba | Qué cubre |
|---|---|
| [`ComponentesComunesTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/finanzas/ComponentesComunesTest.kt) | Los componentes de `ui/components` montados solos, sin actividad ni base: `TextoDinero`, `TarjetaCifra`, `TarjetaValor`, `EstadoVacio`, `SeccionTitulo` |
| [`NavegacionTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/finanzas/NavegacionTest.kt) | Arranque sobre la app real, las cinco pestañas, ir y volver entre ellas, y que el botón de Capturar retire la barra de abajo |

Cuatro cosas que hay que saber antes de escribir más:

- **Los nombres van en camelCase**, no entre backticks. Los espacios en nombres de método solo son legales desde la API 30 y el `minSdk` es 26.
- **La app arranca bloqueada** hasta que termina de leer las preferencias. `NavegacionTest` espera con `waitUntil` a que aparezca la primera pestaña; sin eso la prueba mira el telón y falla por una carrera ajena a lo que se prueba.
- **Todo se busca con `useUnmergedTree = true`.** `NavigationBarItem` y el botón flotante marcan `mergeDescendants`, así que en el árbol fusionado sus textos dejan de ser nodos propios y se hunden en el del contenedor: cualquier selector por descendiente encuentra cero. Compose lo dice en el propio error — *"the unmerged tree contains 1 node that matches"*—, pero cuesta un rato leerlo.
- **No hay `testTag` en la interfaz.** Se selecciona por texto visible, que en una app monolingüe es estable. Para las pestañas se apunta al contenedor `isSelectable()` y no al texto suelto, porque el título también puede aparecer dentro de la pantalla.

Si la suite tarda unos minutos, el teléfono puede dormirse a medio camino y la siguiente prueba falla con `No compose hierarchies found in the app`: la actividad nunca llegó a primer plano. No es un fallo real. Despierta la pantalla antes de correrlas:

```bash
adb shell input keyevent KEYCODE_WAKEUP
```

El diálogo de huella y el de credencial del sistema son UI del sistema operativo: Espresso y Compose no los alcanzan, y probarlos exige UI Automator.

## Convenciones del código

- **Todo en español**: nombres de clases, funciones, variables y comentarios. Los nombres de prueba van en backticks y en prosa (`` `el modo compacto emite exactamente ocho columnas` ``) — salvo en `androidTest`, donde van en camelCase porque los espacios solo son legales desde la API 30.
- **Los comentarios explican el porqué, no el qué.** Si una decisión tiene una alternativa obvia que se descartó, el comentario dice por qué se descartó.
- **Sin acentos en los comentarios y literales del código** (la documentación de `docs/` sí los usa).
- **Una pantalla por archivo**, con su ViewModel arriba y los composables privados abajo.
- **La escritura pasa por el repositorio.** Las pantallas no tocan los DAO.
- **Los importes son centavos en `Long`**, nunca decimales flotantes.
- Las cadenas visibles están en el código, no en `strings.xml`: la app es monolingüe por diseño. En `strings.xml` solo viven el nombre, el lema y los textos del canal de notificaciones.

## Añadir una pantalla

1. Crea el archivo en `ui/screens/` con su `ViewModel` y su composable.
2. Declara la ruta en [`ui/nav/Destinos.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/nav/Destinos.kt) (`Destino` si es pestaña, `Rutas` si cuelga de una).
3. Regístrala en el `NavHost` de [`ui/OllinRaiz.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/OllinRaiz.kt).
4. Si la ruta lleva argumentos, agrégalos con `navArgument` y su `defaultValue`; el patrón de la app es usar `0L` como "sin valor" y filtrarlo con `takeIf { it > 0L }`.

## Añadir un tutorial

Agrega una entrada a `TUTORIALES` en [`ui/screens/TutorialesPantalla.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/screens/TutorialesPantalla.kt), con su clave, resumen, pasos y, si lo hay, el truco: lo que casi nadie descubre solo y evita el error más común del tema.

Los pasos son texto plano y no capturas de pantalla a propósito: una captura envejece con el primer cambio de la interfaz y después miente, mientras que el orden de los pasos sigue siendo cierto.

## Añadir una versión de la base

Ver el final de [modelo de datos](modelo-de-datos.md#migraciones).
