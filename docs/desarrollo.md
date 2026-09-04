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
| Node (solo para el sitio) | 22 |

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
- **`room.schemaLocation`** — KSP escribe los esquemas en `app/schemas/`, que sí se versionan: son el contrato de cada versión de la base y la referencia contra la que se escribe la migración siguiente.
- **La versión sale de `CHANGELOG.md`** — el `versionName` y el `versionCode` se derivan del primer encabezado `## [x.y.z]`, no del `build.gradle.kts`. Ver [publicación](publicacion.md).
- **`buildConfig = true`** — solo para `URL_ACTUALIZACIONES`: la dirección que consulta la app tiene que quedar dentro del APK, y este es el único camino para ponerla ahí sin escribirla a mano en el código.
- **`androidx.fragment` fijado a mano** — `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`: su `FragmentActivity` rechaza los request codes de más de 16 bits que genera `activity` 1.10.1, y **cualquier** selector de archivos revienta al abrirse. Quitar esa línea de `libs.versions.toml` vuelve a romper importar y exportar.
- **`testOptions.unitTests`** con `isIncludeAndroidResources` y `isReturnDefaultValues`, para que las pruebas en la JVM no tropiecen con las clases stub de Android.
- **`release`** con `isMinifyEnabled` e `isShrinkResources`.

La pantalla de Acerca de lee la versión del **paquete instalado** (`PackageManager`) y no de `BuildConfig`: así muestra la que de verdad está corriendo en el teléfono.

## Pruebas

Hay dos suites: **204 pruebas unitarias** en la JVM y **11 pruebas de interfaz** que necesitan dispositivo. Las unitarias y el lint corren en cada push y cada PR ([`pruebas.yml`](../.github/workflows/pruebas.yml)).

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
| [`RecordatoriosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/RecordatoriosTest.kt) | Qué avisa y qué no: la ventana, lo vencido primero, planes apagados o ya terminados, y la fecha formateada para una persona. También cuándo se programa la alarma: la hora en punto, la madrugada, el fin de mes, una hora movida por el usuario y una imposible guardada en disco |
| [`ImportadorExcelTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorExcelTest.kt) | Round trip exportar→importar, sinónimos de encabezado, renglones incompletos, emparejado de transferencias e inferencia de tipo de cuenta |
| [`ImportadorHojasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorHojasTest.kt) | El regreso de Diccionarios, Presupuesto y Compromisos: naturaleza declarada, jerarquía de categorías, metas por mes, compromisos reconstruidos desde el próximo pago y el libro sin hoja de movimientos |
| [`ExcelRoundTripTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExcelRoundTripTest.kt) | Serial de fechas, letras de columna, centavos sin error acumulado, escritura y relectura del libro en ambos esquemas, escapado de XML, exportación parcial y libro vacío |
| [`ExportadorBordesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExportadorBordesTest.kt) | Compromisos con datos, catálogos incompletos, nombres que obligan a entrecomillar, y tres años de movimientos diarios |
| [`XlsxLectorSeguridadTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/XlsxLectorSeguridadTest.kt) | Que el lector rechace un `DOCTYPE` —la bomba de entidades— y respete el tope de tamaño |
| [`EsquemaDeBaseTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/EsquemaDeBaseTest.kt) | La guardia de la base: cada versión con su json exportado y la cadena de migraciones sin huecos. Ver [modelo de datos](modelo-de-datos.md#migraciones) |
| [`RespaldosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/RespaldosTest.kt) | Cuándo toca recordar un respaldo: la semana desde el último, el ancla cuando no hay ninguno, el reloj movido hacia atrás y qué dice el aviso. Ver [seguridad](seguridad.md#el-recordatorio) |
| [`PreferenciasHeredadasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/PreferenciasHeredadasTest.kt) | Actualizar por encima de una versión anterior: unas preferencias escritas por la 1.0.0 se leen sin cerrar la app, y una clave con el tipo equivocado se trata como ausente. Ver [modelo de datos](modelo-de-datos.md#las-preferencias-también-son-datos-guardados) |
| [`ActualizacionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ActualizacionesTest.kt) | El aviso de versión nueva sin red: comparación semántica (`1.10.0` es posterior a `1.9.0`), el `version.json` con sus aristas, el salto de redirección que solo se sigue hacia `https`, cuándo toca preguntar y que un día sin respuesta no gaste el turno |

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

### La prueba de actualización

En [`actualizacion.yml`](../.github/workflows/actualizacion.yml), y la única que mira lo que le pasa a alguien que **ya tenía la app instalada**.

Compila dos APK de depuración desde git —el de la última etiqueta y el de esta rama—, instala el primero, lo abre para que escriba sus preferencias, instala el segundo encima sin desinstalar, y comprueba que sigue abriéndose. La lógica vive en [`actualiza-y-abre.sh`](../.github/scripts/actualiza-y-abre.sh) y se puede correr contra un teléfono conectado:

```bash
RUNNER_TEMP=/tmp .github/scripts/actualiza-y-abre.sh
```

Tres decisiones que la sostienen:

- **Los dos APK se compilan aquí, no se descarga la release publicada.** La llave de depuración la genera el propio runner, así que las dos versiones quedan firmadas igual y una se puede instalar sobre la otra; con firmas distintas Android lo rechaza. De paso, el flujo no necesita los secretos de firma.
- **Se afirma poco a propósito**: que el proceso siga vivo y que no haya excepción mortal. No mira la pantalla, porque un fallo de arranque se manifiesta como el proceso que desaparece y eso se ve sin depender de animaciones.
- **La versión anterior tiene que llegar a escribir**: es lo que da sentido a todo. El fallo que motivó esta prueba estaba en *leer* lo que la versión vieja dejó, no en instalar por instalar.

**Hoy informa sin bloquear**, y ahí está la deuda. La intención es que bloquee, en el mismo grupo que las migraciones: un fallo así deja sin app a toda la gente que actualizó y no se arregla desde fuera. Pero al ponerla a detener publicaciones dio **tres falsos negativos seguidos** contra la 1.0.3, una versión que abre perfectamente en un teléfono real:

1. El script moría en silencio si el lanzador devolvía un código distinto de cero — sin mensaje ni log, sin forma de saber si la culpa era de la app o de la prueba.
2. `am start` a secas entregaba el intent a la tarea que sobrevive a `install -r`, sin levantar ningún proceso: la prueba medía un arranque que nunca ocurrió. De ahí el `-S`.
3. El tercero sigue sin diagnóstico, y por eso existe el experimento de control: cuando el proceso no queda vivo, la prueba desinstala, instala la misma versión en limpio y lo reintenta, para decir si el problema es *actualizar* o es *esa compilación*.

Una puerta que detiene publicaciones buenas se acaba ignorando, y una puerta ignorada no protege de nada. Vuelve a `needs` de `publicar` en cuanto se le vea pasar contra una versión conocida buena.

Al invocarse desde la publicación, el árbol ya es el de la etiqueta que se publica, así que esa etiqueta se excluye al buscar «la anterior» — si no, la prueba instalaría una versión sobre sí misma y no probaría nada.

Existe porque la 1.0.1 se cerraba al abrirse en los teléfonos que venían de la 1.0.0, y las 190 pruebas de entonces no podían verlo: todas empiezan con el disco vacío. Ver [modelo de datos](modelo-de-datos.md#las-preferencias-también-son-datos-guardados).

Si la suite tarda unos minutos, el teléfono puede dormirse a medio camino y la siguiente prueba falla con `No compose hierarchies found in the app`: la actividad nunca llegó a primer plano. No es un fallo real. Despierta la pantalla antes de correrlas:

```bash
adb shell input keyevent KEYCODE_WAKEUP
```

El diálogo de huella y el de credencial del sistema son UI del sistema operativo: Espresso y Compose no los alcanzan, y probarlos exige UI Automator.

## Integración continua

Cuatro flujos, todos con **JDK 21**, en [`.github/workflows/`](../.github/workflows):

| Flujo | Cuándo | Qué corre |
|---|---|---|
| `pruebas.yml` | push a `main` y cada PR | `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`, `assembleRelease` y el build del sitio |
| `pruebas-instrumentadas.yml` | lunes, y a mano | La suite de interfaz sobre un emulador |
| `actualizacion.yml` | al etiquetar, lunes, y a mano | Instala la versión nueva sobre la anterior y comprueba que abre |
| `publicacion.yml` | tag `vX.Y.Z` | Comprueba la etiqueta contra el CHANGELOG, invoca `pruebas.yml`, firma y publica el APK |
| `sitio.yml` | `web/**`, `CHANGELOG.md`, o al terminar una publicación | Construye el sitio y lo publica en GitHub Pages |

`publicacion.yml` **invoca** a `pruebas.yml` y a `actualizacion.yml` con `workflow_call` en vez de copiar sus pasos: una etiqueta no puede pasar por una comprobación más floja que un pull request cualquiera. Los dos bloquean la publicación — si fallan, no se firma nada ni se crea la release.

Cuando CI falla, el reporte HTML de pruebas y el de lint quedan como artefacto del run durante 14 días — se leen mucho mejor que el rastro de la consola.

Lo mismo que corre allá corre aquí:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest lintDebug assembleDebugAndroidTest assembleRelease
```

El proceso completo de publicar una versión está en [publicación](publicacion.md).

## El sitio

En [`web/`](../web): Vite sin framework, cuatro archivos de fuente y ninguna dependencia en tiempo de ejecución.

```bash
npm --prefix web install
npm --prefix web run dev      # http://localhost:5173/ollin-finanzas/
npm --prefix web run build
```

`public/version.json` y `src/version.js` los genera el build desde `CHANGELOG.md` y están en `.gitignore`: son derivados, no fuentes. Detalles en [el sitio](sitio.md).

## Convenciones del código

- **Todo en español**: nombres de clases, funciones, variables y comentarios. Los nombres de prueba van en backticks y en prosa (`` `el modo compacto emite exactamente ocho columnas` ``) — salvo en `androidTest`, donde van en camelCase porque los espacios solo son legales desde la API 30.
- **Los comentarios explican el porqué, no el qué.** Si una decisión tiene una alternativa obvia que se descartó, el comentario dice por qué se descartó.
- **Sin acentos en los comentarios y literales del código** (la documentación de `docs/` y el sitio de `web/` sí los usan).
- **`.editorconfig` fija el estilo**: LF, UTF-8, cuatro espacios, 100 columnas en Kotlin y sin imports con comodín.
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

## Cambiar el esquema de la base

Cuatro pasos —cambiar las entidades, subir `VERSION_BASE`, compilar para que KSP exporte el `N.json`, y escribir la migración— explicados con su ejemplo en [modelo de datos](modelo-de-datos.md#migraciones). `EsquemaDeBaseTest` falla en CI si falta cualquiera de los cuatro.
