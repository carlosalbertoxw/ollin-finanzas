# Ollin

**El libro de tus movimientos.**

App Android de finanzas personales: registra lo que entra y lo que sale, lo clasifica, y
te dice en qué se te está yendo el dinero. *Ollin* es "movimiento" en náhuatl, y es el
glifo del calendario mexica que representa el cambio — que es exactamente lo que registra
un libro de finanzas.

---

## Qué hace

| | |
|---|---|
| **Movimientos** | Entradas, salidas, transferencias y saldos de apertura. Captura con listas cerradas: sin categorías escritas a mano que después no coinciden entre sí. |
| **Transferencias en una sola captura** | Mueves dinero entre dos cuentas tuyas una vez y la app genera los dos renglones ligados por un mismo grupo. Borrar uno borra el otro, así que nunca queda media transferencia. |
| **Categorías con padre e hija** | La categoría clasifica y la descripción detalla. Son dos cosas distintas y viven en columnas distintas, para que un rubro repartido en cinco descripciones se vea como el rubro grande que es. |
| **Patrimonio aparte del gasto** | Categorías de tipo **Patrimonio** y cuentas de tipo **Activo**. Comprar terreno, cripto o un celular es trasladar patrimonio, no gastarlo, y los tableros lo separan del consumo real. |
| **Contraparte derivada** | No se captura si el movimiento es entre tus cuentas o con un tercero: se deduce del tipo. Traspaso o saldo inicial → propia; entrada o salida → tercero. Un campo que se captura a mano es un campo que se deja de mantener. |
| **Salud de los datos** | Una pantalla que revisa en continuo: tipos que contradicen al signo del importe, transferencias sin su pareja, movimientos sin categoría, medios incoherentes con la cuenta, y descripciones casi idénticas (Levenshtein ≤ 2). Varios hallazgos se reparan con un botón. |
| **Presupuesto y analítica** | Meta contra realidad por categoría y tendencia mensual. |
| **Compromisos** | Lo que ya está comprometido y aún no se paga: mensualidades, suscripciones, gastos anuales. Cada uno lleva su cuenta, su categoría, su periodicidad y la fecha del siguiente pago, y avisa antes de vencer. **Pagar** no escribe nada por su cuenta: abre la captura ya llena para que corrijas el monto si cambió, y el plan solo avanza cuando el movimiento queda guardado. |
| **Importar y exportar .xlsx** | Tu respaldo es un libro de Excel que tú decides dónde guardar. |

Todos los importes viven como **centavos en un `Long`**. Nunca como decimal flotante: así
un saldo cero es cero exacto y las conciliaciones cuadran.

---

## Import / export

- **Importar** lee un `.xlsx` y reconoce los encabezados sin importar acentos ni
  mayúsculas, en cualquiera de los dos esquemas que la app exporta. Al entrar corrige lo
  que encuentre: alinea el tipo con el signo, empareja transferencias, crea las cuentas
  que falten y clasifica por catálogo.
- **Exportar** es configurable en dos ejes:
  - **Esquema**: `Extendido` (agrega Categoría, Año, Nota, Compromiso) o `Compacto`
    (las ocho columnas esenciales).
  - **Pestañas**: eliges cuáles de las siete generar. `Registros` siempre va, porque es
    la fuente de las fórmulas de todas las demás.

Pestañas disponibles: `Balance`, `Ingresos - Egresos`, `Presupuesto`, `Transferencias`,
`Compromisos`, `Registros`, `Diccionarios`.

### Por qué fórmulas y no tablas dinámicas

Las hojas de análisis salen con `SUMIFS` vivos en vez de dinámicas. Una dinámica exige
refresco manual, se comporta distinto según la suite, y deja cachés huérfanos si exportas
solo algunas pestañas. Las fórmulas se recalculan solas y funcionan igual en Excel, WPS
Office, LibreOffice y Google Sheets. Cada celda lleva además el valor ya calculado, así
que la hoja se ve bien incluso en visores que no recalculan.

Los criterios de fecha usan `DATE(año,mes,día)` en vez de `TEXT(...,"yyyy-mm")`, que
depende del idioma de la suite.

---

## Arquitectura

Un solo módulo, Kotlin + Jetpack Compose (Material 3).

```
mx.ollin.finanzas
├── data
│   ├── db/       Room: entidades, DAOs, semilla del catálogo
│   ├── excel/    Lector y escritor .xlsx propios
│   ├── prefs/    DataStore
│   ├── notify/   Recordatorios de compromisos
│   └── repo/     Repositorio único
├── domain
│   ├── model/    Enums, Dinero (centavos)
│   └── usecase/  RevisaCalidad, ReparaDatos
└── ui
    ├── theme/    Paleta Ollin
    ├── components/
    └── screens/
```

Decisiones que no son las de default, y por qué:

- **XLSX escrito a mano** sobre `java.util.zip` + SAX. Apache POI en Android pesa ~15 MB,
  mete decenas de miles de métodos y obliga a desugaring; aquí el formato producido está
  bajo control y el escritor completo cabe en ~400 líneas.
- **Sin Hilt.** Con un módulo y media docena de objetos compartidos, un contenedor a mano
  (`di/Contenedor.kt`) se lee de arriba a abajo y no cuesta tiempo de compilación.
- **Las entidades de Room son el modelo de dominio.** Duplicarlas en otra capa sería mapeo
  sin ganancia a esta escala.
- **Importes en centavos (`Long`).** Ver arriba.
- **Alarma inexacta** para los recordatorios: no justifica pedir permiso de alarma exacta
  ni gastar batería.
- **`androidx.fragment` declarado a mano.** `biometric:1.1.0` arrastra `fragment:1.2.5`,
  anterior a la API de ActivityResult: su `FragmentActivity` rechaza los request codes de
  más de 16 bits que genera `activity:1.10.1`, y **cualquier** selector de archivos revienta
  al abrirse. Quitar esa línea de `libs.versions.toml` vuelve a romper importar y exportar.

---

## Privacidad

Todo vive en el teléfono. No hay cuentas, ni servidor, ni analítica. El respaldo
automático del sistema está **desactivado** para la base de datos a propósito: tu
respaldo es la exportación a `.xlsx`, que tú decides dónde guardar.

---

## Compilar

Requiere **JDK 17 a 21** y Android SDK 36. Kotlin 2.1.20 no arranca con JDK 25 ni 26: su
compilador no sabe leer esas versiones y la compilación muere con un mensaje que es solo
el número de versión, sin más pista:

```
* What went wrong:
26.0.1
```

Ojo con el JBR que trae Android Studio: en instalaciones recientes ya es 25 y falla igual.
Apunta `JAVA_HOME` a un JDK 21 — Android Studio suele dejar uno en `~/.jdks/`.

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew :app:assembleDebug
```

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew :app:testDebugUnitTest
```

Las pruebas de `ExcelRoundTripTest` generan libros reales en `app/build/pruebas/`, útiles
para abrirlos a mano y comprobar el resultado.

- `minSdk` 26 · `targetSdk` 36 · Kotlin 2.1.20 · AGP 8.10.0 · Gradle 8.14.5
