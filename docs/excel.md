# Exportación e importación en Excel

La pestaña **Archivo** genera un `.xlsx` con todo el libro y lo vuelve a leer. Es el respaldo real de la app: la base cifrada no se puede restaurar en otro teléfono.

Todo el paquete `data/excel/` es propio, **sin dependencias externas**. Apache POI pesa del orden de 15 MB en Android, mete decenas de miles de métodos y obliga a desugaring; aquí el formato producido está bajo control, así que un escritor de ~400 líneas es más pequeño, arranca más rápido y no sorprende.

## El libro que sale

### Hojas

Se eligen desde la pantalla de Archivo ([`HojaExportable`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/CatalogoHojas.kt)):

| Hoja | Contenido |
|---|---|
| **Balance** | Saldo vivo de cada cuenta agrupado por naturaleza, patrimonio neto, gasto mensual promedio y meses de fondo de emergencia |
| **Ingresos - Egresos** | Categorías contra meses. La compra de patrimonio va en su propio bloque, aparte del gasto |
| **Presupuesto** | Meta contra realidad por categoría, con desviación y avance |
| **Transferencias** | Entradas y salidas internas por cuenta, con el neto que debe cuadrar en cero |
| **Compromisos** | Mensualidades, suscripciones y gastos anuales por venir, con cuenta, categoría y saldo pendiente |
| **Registros** | Todos los movimientos, uno por renglón. Obligatoria: es la fuente de las demás |
| **Diccionarios** | Cuentas, categorías, medios, contrapartes y tipos; alimentan los desplegables de Registros |

Las pestañas salen en orden de lectura natural: primero el análisis, luego el detalle y los catálogos al final. El preajuste "solo datos" (`HojaExportable.MINIMA`) deja Registros y Diccionarios: los movimientos y el catálogo del que cuelgan. Para un respaldo que también devuelva metas y compromisos hay que exportar el libro completo.

### Esquemas de columna

[`EsquemaExportacion`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/CatalogoHojas.kt) decide el ancho de la hoja Registros:

- **Extendido** — `Fecha, Cantidad, Cuenta, Categoria, Descripcion, Medio, Contraparte, Tipo, Mes, Anio, Nota, Compromiso`. Conserva todo.
- **Compacto** — `Fecha, Cantidad, Cuenta, Descripcion, Medio, Contraparte, Tipo, Mes`, las ocho indispensables.

Las fórmulas de las hojas de análisis resuelven sus referencias de columna a partir del esquema elegido. Cuando no hay columna Categoría —modo compacto— **la agrupación cae a Descripción**, y las hojas lo dicen en su subtítulo en vez de salir vacías.

En modo compacto la contraparte sale como su código numérico (1 o 2) en vez de su etiqueta.

### Fórmulas vivas, no tablas dinámicas

Las hojas de análisis llevan fórmulas reales (`SUMIFS`, `COUNTIFS`, `SUM`) apuntando a Registros, más el valor ya calculado como caché. La hoja se ve bien al abrirla en cualquier visor y sigue viva si editas un renglón: cambias un importe y los totales se mueven solos.

Por qué no dinámicas: exigen refresco manual y hasta entonces muestran números viejos; las fórmulas se comportan igual en Excel, WPS, LibreOffice y Sheets, y permiten exportar solo algunas pestañas sin dejar cachés huérfanos. El libro se marca con `fullCalcOnLoad`.

Dos detalles de las fórmulas:

- **Los criterios de fecha usan `DATE(año,mes,día)`** y no `TEXT(...,"yyyy-mm")`, que depende del idioma de la suite.
- **El criterio de categoría apunta a la celda A de su propio renglón** (`$A12`), no al texto literal. Así el usuario puede renombrar la categoría dentro de la hoja y el cálculo la sigue. Por lo mismo los renglones hijos **no llevan sangría**: `"   Gasolina"` no coincidiría con `"Gasolina"` en la columna Categoría.
- Los totales de bloque suman **solo las filas hoja** (`B5+B6+B9…`), nunca los renglones padre, que duplicarían.

El libro incluye además anchos de columna, panel congelado en el encabezado, validaciones de lista contra Diccionarios y un `ListObject` (tabla de Excel) sobre Registros, para que el filtro y el formato crezcan solos al agregar renglones a mano.

## Cómo está hecho

| Archivo | Papel |
|---|---|
| [`ModeloHoja.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/ModeloHoja.kt) | `Celda` (texto, número, fecha, booleano, fórmula), `Hoja`, anchos, validaciones, tablas y los índices de estilo |
| [`Ooxml.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/Ooxml.kt) | Seriales de fecha, letras de columna, escape de XML, saneo de nombres de hoja |
| [`XlsxEscritor.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/XlsxEscritor.kt) | Serializa el paquete OOXML completo dentro de un ZIP |
| [`XlsxLector.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/XlsxLector.kt) | Lee un `.xlsx` con el SAX del JDK |
| [`DatosExportacion.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/DatosExportacion.kt) | Fotografía de los datos en el momento de exportar |
| [`ExportadorExcel.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/ExportadorExcel.kt) | Arma las hojas |
| [`ImportadorExcel.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/ImportadorExcel.kt) | Vuelca un libro en la base |
| [`LectorCatalogos.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/LectorCatalogos.kt) | Interpreta Diccionarios, Presupuesto y Compromisos; no toca la base |

Los índices de estilo de `Estilo` deben coincidir en orden exacto con `cellXfs` en `XlsxEscritor.estilosXml()`.

Excel cuenta los días desde el 30/12/1899 (desplazamiento 25 569): `2026-01-01` es el serial 46023. Si eso cambia, todas las fechas exportadas se corren, y por eso tiene prueba propia.

El lector carga el paquete completo en memoria porque `sharedStrings.xml` puede venir después de las hojas dentro del ZIP; para un libro de finanzas personales el costo es irrelevante y evita necesitar acceso aleatorio. Hay un tope de 64 MB por archivo.

## Importación

`Archivo → Importar` abre el selector del sistema. Es la única entrada: el archivo lo eliges tú, dentro de la app.

Hubo un `intent-filter` de tipo `spreadsheetml.sheet` para aceptar "Abrir con Ollin Finanzas" desde un gestor de archivos o desde la nube, pero **nadie leía el `Intent` entrante**: elegir el archivo abría el tablero y lo ignoraba. Se retiró hasta que haya con qué atenderlo. Cuando se implemente, la URI llega de otra app —el filtro es `exported` y `BROWSABLE`— así que es entrada no confiable: hay que importarla solo tras confirmación explícita, nunca al vuelo, porque importar con Reemplazar borra el libro.

### Qué hojas se leen

Los movimientos salen de la primera hoja que traiga, al menos, columnas de **fecha**, **cantidad** y **cuenta**; se prefiere la llamada `Registros`. Admite libros que no salieron de aquí.

Además entran, **si el libro las trae**, las otras tres pestañas que la app sabe generar:

| Hoja | Qué vuelve |
|---|---|
| **Diccionarios** | Cuentas con su naturaleza y categorías con su grupo. Entran incluso las cuentas sin un solo movimiento |
| **Presupuesto** | Las metas por categoría y mes |
| **Compromisos** | Mensualidades, suscripciones y gastos anuales por venir |

Se leen en ese orden y antes que los movimientos, porque el catálogo manda: una cuenta creada desde Diccionarios nace con la naturaleza **declarada** en la hoja, mientras que una creada desde el nombre de un movimiento solo la adivina.

Un libro de puros catálogos —sin hoja de movimientos— también sirve: entra lo que traiga y **los movimientos actuales no se tocan**, ni siquiera con "reemplazar todo" encendido. Vaciarlos ahí borraría todo a cambio de nada. Lo mismo al revés: un libro sin pestaña Presupuesto no borra las metas que ya tienes, porque no podría restituirlas.

Lo que no vuelve, y hay que arreglar a mano:

- **El tipo de categoría no viaja en Diccionarios.** Al importar se deduce del uso en Registros —lo que solo aparece en entradas es ingreso, el resto gasto—, así que una categoría de **patrimonio regresa como gasto**. Se avisa cuando se crea alguna. Las categorías que ya existen no se tocan.
- **Compromisos guarda el próximo pago y los pagos que faltan**, no la fecha original ni los ya cubiertos: son los dos datos que sirven para vigilar lo que viene. Al volver a entrar, el compromiso se reconstruye desde ahí —arranca en el próximo pago con los pagos que quedan—, salvo que el libro traiga columnas de `fecha primer pago`, `total pagos` o `pagos realizados`.

La hoja Compromisos lleva columna `Categoria` —`Compromiso, Cuenta, Categoria, Monto, Periodicidad, Proximo pago, Pagos restantes, Saldo pendiente`— justamente para que el rubro del pago sobreviva al viaje. Se resuelve contra el catálogo, igual que la cuenta: si nombra una categoría que no existe, el compromiso entra sin ella y se avisa, en vez de inventarla.

También se aceptan estas tres hojas capturadas a mano: el encabezado se busca por sinónimos en cualquier renglón, no solo en el primero. Presupuesto admite tanto el formato de la app —un bloque por mes, con el periodo como subtítulo— como una tabla con columnas propias de `Anio` y `Mes`. Una meta cuya categoría no existe **se avisa en vez de inventar la categoría**: crearla sería adivinar su naturaleza sin un solo movimiento que la respalde.

### Reconocimiento de columnas

Los encabezados se comparan sin acentos ni mayúsculas y con sinónimos:

| Campo | Alias reconocidos |
|---|---|
| fecha | fecha, date, dia |
| cantidad | cantidad, importe, monto, amount |
| cuenta | cuenta, account |
| categoría | categoria, category, rubro |
| descripción | descripcion, concepto, detalle, description |
| medio | medio, forma de pago, metodo |
| contraparte | contraparte, persona |
| tipo | tipo, type, movimiento |
| nota | nota, notas, comentario |
| compromiso | compromiso |

Las fechas se aceptan como serial de Excel o como texto en ISO, `dd/MM/yyyy`, `d/M/yyyy`, `MM/dd/yyyy`, `yyyy/MM/dd` y `dd-MM-yyyy`. Los importes, como número o como texto (`$1,234.56`, `(455.33)`).

### No copia: corrige al entrar

Una hoja de cálculo se degrada sola, así que el importador arregla lo que puede mientras lee:

1. **Alinea el tipo con el signo** del importe cuando se contradicen, y lo reporta renglón por renglón. El importe nunca se toca.
2. **Empareja transferencias**: busca pares de salida y entrada con misma fecha e importe absoluto. Primero exige que la descripción coincida y luego afloja esa condición. Cada par recibe un uuid de grupo; lo que queda suelto se reporta como huérfano.
3. **Crea las cuentas que falten**, infiriendo su tipo del nombre: "MSI" → crédito MSI; "préstamo a…" o "por cobrar" → activo (es dinero tuyo que va a volver, no una deuda); "tarjeta", "crédito", "hipoteca" → crédito; "cartera", "efectivo", "caja" → efectivo; "terreno", "cripto", "inmueble" → activo; lo demás, cuenta de banco. Solo lo que parece tarjeta queda atado al medio electrónico.
4. **Clasifica** en tres pasos: la columna Categoría manda si viene; si no, el mapeo por descripción (primero la variante específica por tipo, `intereses|SALIDA`); si no, coincidencia directa con una categoría existente. Lo que no se resuelve queda sin categoría y se reporta.
5. **Recalcula la contraparte** a partir del tipo en vez de confiar en la columna.

Sin columna Tipo, el tipo se infiere de la descripción y del signo: "balance inicial" o "saldo inicial" → saldo inicial; "revaluación", "depreciación", "plusvalía" → ajuste de valor; "transferencia", "traspaso", "pago tarjeta" → transferencia; lo demás, entrada o salida según el signo.

Un renglón sin fecha, sin cantidad o sin cuenta se omite y se reporta con su número de fila.

### Opciones

- **Reemplazar** (por omisión) vacía los movimientos antes de importar —y también las metas y los compromisos, pero solo si el libro trae esas pestañas—; si se apaga, agrega. Al agregar, el nombre hace de identidad del compromiso: importar dos veces el mismo libro no deja la lista duplicada.
- Reemplazar alcanza también al **catálogo que queda sin uso**: una vez cargado el archivo, se van las cuentas y categorías que no sostienen ningún movimiento, meta ni compromiso y que el libro no nombra. Ahí caen las cuentas de ejemplo que siembra la app en el primer arranque, que si no se quedaban para siempre ensuciando cada desplegable. No se toca nada con datos detrás, ni una cuenta que venga en Diccionarios aunque no tenga movimientos, ni un grupo que todavía agrupe a una categoría viva; los mapeos de descripción que apuntaban a una categoría borrada se limpian con ella. Si el libro no trae ninguna categoría —el esquema compacto no las exporta— el catálogo se queda como estaba: vaciarlo dejaría la captura sin dónde clasificar y la siembra las repondría igual.
- **Corregir al importar** (por omisión) enciende la alineación de tipo con signo y el recálculo de contraparte.
- El emparejado de transferencias, la creación de cuentas faltantes y la lectura de las otras pestañas van siempre.

### Todo o nada

**Importar es atómico.** La lectura del `.xlsx` va fuera de la transacción y toda la escritura dentro de un único `db.withTransaction`: movimientos, metas, compromisos y la purga de catálogo. Por eso [`ImportadorExcel`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/ImportadorExcel.kt) recibe la base entera y no sus DAO sueltos.

No es un detalle de estilo. Con **Reemplazar** encendido —que es lo normal— la importación borra todos los movimientos antes de insertar los nuevos, y entre una cosa y otra todavía crea categorías. Suelto, cualquier tropiezo en ese tramo —disco lleno, una restricción, o que Android mate el proceso por memoria— dejaba el libro **sin nada y sin reemplazo**: la peor pérdida posible en una app cuyo valor entero es el registro, e irrecuperable salvo que conserves el `.xlsx`.

Parsear fuera de la transacción también importa: es la parte que puede quedarse sin memoria con un archivo grande, y así revienta antes de haber borrado nada.

### Resultado

`ResultadoImportacion` devuelve filas leídas, importadas, omitidas, cuentas y categorías creadas y eliminadas, cuántas quedaron sin categoría, tipos corregidos, contrapartes recalculadas, transferencias emparejadas y huérfanas, metas y compromisos importados, más una lista de diagnósticos con severidad `INFO`, `AVISO` o `ERROR` y su número de fila.

Los diagnósticos se muestran en la propia tarjeta del resumen, agrupados por mensaje y con los renglones que lo provocaron (`diagnosticosAgrupados()`): veinte filas rotas son una línea con veinte renglones, no veinte líneas idénticas. Hablan del **archivo**, y casi ninguno deja rastro en la base, así que la pantalla de Salud no los conoce: esa se ofrece aparte y solo cuando la auditoría —que corre sobre los datos ya importados— encontró algo de verdad.

Los fallos se traducen a mensajes accionables —archivo que no es un `.xlsx`, permiso perdido sobre el archivo, sin espacio, libro demasiado grande para la memoria—; la excepción cruda se manda a logcat sin datos del usuario.

## Escribir el archivo

El destino se abre con modo `"wt"` (escribir truncando): al sobrescribir un archivo más grande, sin truncar quedaría la cola del viejo pegada al final y el `.xlsx` saldría corrupto. Pero varios proveedores de documentos —gestores de archivos y servicios de nube— no soportan ese modo, así que se cae a `"w"`; un archivo recién creado por el selector nace vacío, y eso es preferible a no poder exportar.

El selector propone la carpeta Descargas como punto de partida. Es solo una sugerencia: si el destino elegido no admite crear el archivo, quien avisa es el propio selector — la app no recibe ningún uri y no puede distinguir ese caso de una cancelación.

## Pruebas

- [`ExcelRoundTripTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExcelRoundTripTest.kt) — el escritor y el lector reales: seriales de fecha, letras de columna, centavos sin error acumulado, encabezados exactos de cada esquema, escapado de comillas y acentos, exportar solo algunas pestañas y un libro vacío.
- [`ExportadorBordesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ExportadorBordesTest.kt) — lo que el round trip no toca: compromisos con datos, catálogos incompletos (categoría cuyo padre no existe, cuenta con apóstrofo, movimientos que apuntan a ids inexistentes) y tres años de movimientos diarios.
- [`ImportadorExcelTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorExcelTest.kt) — el lado que decide cosas por ti: corrección de tipos, emparejado de transferencias, cuentas inventadas y contrapartes recalculadas.
- [`ImportadorHojasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/ImportadorHojasTest.kt) — el viaje de regreso de Diccionarios, Presupuesto y Compromisos, incluido el libro completo de ida y vuelta: si alguien mueve una columna del exportador, la prueba se entera.

El lector XML prohíbe el `DOCTYPE` y las entidades externas, y **aborta si el parser de la plataforma no acepta esas banderas** en vez de seguir sin ellas. Es lo que corta de raíz una bomba de entidades: el tope de 64 MB no la ataja, porque cuenta lo que se lee del zip y no lo que el parser expande después.

Los libros quedan en `app/build/pruebas/` para poder abrirlos a mano y comprobar el resultado.
