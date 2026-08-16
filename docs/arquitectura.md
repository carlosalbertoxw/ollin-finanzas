# Arquitectura

Módulo único (`:app`), Kotlin y Jetpack Compose. Tres capas delgadas y una regla: la escritura pasa por el repositorio, y las pantallas nunca tocan los DAO.

```
Compose (pantallas + ViewModels)
        │  Flow / suspend
        ▼
FinanzasRepositorio  ──►  ExportadorExcel / ImportadorExcel
        │            ──►  RevisaCalidad
        ▼
Room (OllinDatabase, cifrada con SQLCipher)
```

## Capas

### `ui/`

Una pantalla por archivo en `ui/screens/`, cada una con su `ViewModel` declarado en el mismo archivo. Los estados se exponen como `StateFlow` y se consumen con `collectAsStateWithLifecycle`.

No hay `Factory` por pantalla: [`recuerdaVm`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/Fabrica.kt) crea el ViewModel pasándole el `Contenedor` a mano.

```kotlin
val vm = recuerdaVm("archivo") { ArchivoVm(contenedor) }
```

La navegación vive en [`OllinRaiz`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/OllinRaiz.kt), con un `NavHost` de Navigation Compose. Las cinco pestañas inferiores son el enum [`Destino`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/nav/Destinos.kt) —Tablero, Movimientos, Presupuesto, Analítica, Archivo— y el resto de las rutas (captura, transferencia, cuentas, categorías, compromisos, calidad, revisión, ajustes, tutoriales, acerca de) son constantes en `Rutas`.

El botón flotante **Capturar** solo aparece sobre las pestañas principales; en las pantallas que cuelgan de ellas se oculta.

Dos rutas se reescriben al vuelo en vez de apilarse:

- `captura?compromiso={id}` abre la captura precargada con los datos del compromiso que se paga. Guardar escribe el movimiento ligado al compromiso, pero no mueve el plan: eso se decide a mano en la lista de compromisos ([el libro](movimientos.md#compromisos)).
- Al pasar de captura a transferencia se hace `popBackStack()` primero: al guardar, los dos renglones de la transferencia se reescriben y el id que traía la captura deja de existir.

`ui/components/Comunes.kt` concentra lo visual repetido: `TextoDinero` (color por signo y ancho tabular), tarjetas de cifra, `BarrasFlujo`, `LineaEvolucion`, `BarraAvance` y estados vacíos.

### `domain/`

Sin dependencias de Android. Contiene:

- Los enums del modelo ([`TipoMovimiento`, `TipoCuenta`, `TipoCategoria`, `Medio`, `Contraparte`, `Periodicidad`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/model/Enums.kt)) y `normalizaClave()`, que quita acentos y colapsa espacios para comparar texto que viene de una hoja de cálculo.
- [`Dinero`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/model/Dinero.kt): todo importe vive como centavos en un `Long`. Ver [movimientos](movimientos.md#el-dinero-son-centavos).
- Los casos de uso [`RevisaCalidad` y `ReparaDatos`](calidad.md).

### `data/`

- `db/` — Room. Las entidades son también el modelo de dominio: con un solo módulo, duplicarlas en otra capa solo agregaría mapeo. Ver [modelo de datos](modelo-de-datos.md).
- `repo/` — [`FinanzasRepositorio`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/repo/FinanzasRepositorio.kt). Único punto de escritura; ahí viven las reglas que no deben poder saltarse desde la interfaz.
- `excel/` — lector y escritor de `.xlsx` propios, más el exportador e importador del libro. Ver [Excel](excel.md).
- `prefs/` — preferencias en DataStore, expuestas como un `Flow<Ajustes>`.
- `seguridad/` — llave de la base, derivación del PIN y control de bloqueo. Ver [seguridad](seguridad.md).
- `notify/` — recordatorios de compromisos por vencer.

`ReparaDatos` es la excepción a la regla del repositorio: reescribe renglones en lote y trabaja contra los DAO directamente.

## Inyección de dependencias

Manual, en [`Contenedor`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/di/Contenedor.kt): base de datos, repositorio, ajustes, control de bloqueo, revisión de calidad, reparación y sembrador, todos `by lazy`. Se construye una vez en [`OllinApp`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/OllinApp.kt) y se pasa por parámetro a las pantallas.

Con un módulo y media docena de objetos compartidos, Hilt aportaría anotaciones y tiempo de compilación sin resolver ningún problema real.

`OllinApp` además crea el canal de notificaciones, programa la revisión diaria de compromisos y siembra el catálogo inicial en un `CoroutineScope` de IO. El [sembrador](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Sembrador.kt) es idempotente: si ya hay categorías, no toca nada.

## Arranque y bloqueo

[`MainActivity`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/MainActivity.kt) es una `FragmentActivity` y no una `ComponentActivity`, porque el diálogo de huella y credencial del sistema se monta sobre el gestor de fragmentos.

El árbol que se compone depende de dos señales:

| Estado | Qué se dibuja |
|---|---|
| Preferencias sin leer (`null`) | Telón: fondo liso, nunca datos |
| Bloqueado y con modo de bloqueo activo | `BloqueoPantalla` |
| Bloqueado pero sin modo definido aún | Telón |
| Desbloqueado | `OllinRaiz` |

`BloqueoPantalla` **sustituye** al árbol de la app, no lo tapa: si fuera una capa encima, el contenido seguiría compuesto debajo y asomaría en la vista de apps recientes. Mientras hay candado configurado, la ventana lleva `FLAG_SECURE`.

## Flujo de datos

Las consultas de Room devuelven `Flow`; los ViewModels los transforman y publican con `stateIn(SharingStarted.WhileSubscribed(5_000))`. El tablero combina cuatro fuentes —saldos, flujo mensual, compromisos y hallazgos de calidad— en un solo `EstadoTablero` que calcula liquidez, deuda, patrimonio, meses de colchón y tasa de ahorro sobre la marcha.

Los agregados que SQL hace bien (saldo por cuenta, flujo por mes, totales por categoría) viven en los [DAO](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Daos.kt) y devuelven [proyecciones](modelo-de-datos.md#proyecciones). Lo que depende de reglas de negocio —qué cuenta como consumo, qué es traslado de patrimonio— se decide en Kotlin o con un `CASE` explícito sobre el tipo de la categoría.

## Dependencias principales

| Qué | Para qué |
|---|---|
| Compose BOM + Material 3 | Interfaz |
| Navigation Compose | Navegación |
| Room + KSP | Persistencia y esquemas exportados |
| SQLCipher (`net.zetetic:sqlcipher-android`) | Cifrado de la base |
| DataStore Preferences | Ajustes |
| AndroidX Biometric | Credencial del sistema |
| DocumentFile | Selector de archivos para importar y exportar |
| Robolectric + JUnit 4 | Pruebas en la JVM |

No hay dependencia de Apache POI: el paquete `data/excel/` escribe y lee `.xlsx` por su cuenta. Ver [Excel](excel.md#cómo-está-hecho).

`androidx.fragment` se fija explícitamente en `libs.versions.toml`: `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`, y con esa versión cualquier launcher revienta al abrirse —incluido el selector con el que se importa y se exporta.
