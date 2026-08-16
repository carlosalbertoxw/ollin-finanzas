# Modelo de datos

Room sobre SQLite cifrado. Seis tablas, versión de esquema **2**, esquemas exportados en `app/schemas/`.

Las entidades de Room son también el modelo de dominio: [`Entidades.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Entidades.kt).

## Tablas

### `cuenta`

Dónde está el dinero. Índice único por `nombre`.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | Long | Autogenerado |
| `nombre` | String | Único |
| `tipo` | `TipoCuenta` | Decide si el saldo es liquidez, deuda o patrimonio no líquido |
| `medioPorDefecto` | `Medio` | Medio sugerido al capturar. Evita marcar la cartera como electrónica |
| `soloElectronico` | Boolean | Por esta cuenta no puede pasar dinero en mano |
| `limiteCentavos` | Long? | Solo tarjetas; permite mostrar el % de uso |
| `incluirEnPatrimonio` | Boolean | Apagado, la cuenta se registra pero no suma en ninguna cifra agregada |
| `archivada`, `orden`, `colorHex`, `notas` | | |

`soloElectronico` es una regla **de la cuenta y no del tipo**: una tarjeta no se cobra en efectivo, pero un préstamo familiar registrado con el mismo tipo sí puede recibir dinero en mano.

`incluirEnPatrimonio` existe para el dinero que pasa por tus manos sin ser tuyo: se sigue registrando, pero no infla el patrimonio ni los meses de colchón.

### `categoria`

Con qué se clasifica el movimiento. Índice único por (`nombre`, `padreId`).

| Columna | Notas |
|---|---|
| `padreId` | FK a la misma tabla, `SET NULL` al borrar. `null` = categoría raíz |
| `tipo` | `GASTO`, `INGRESO`, `PATRIMONIO`, `TRASPASO` |
| `esencial` | Distingue lo que no puedes dejar de pagar de lo que sí |
| `colorHex`, `archivada`, `orden` | |

Un solo nivel de anidamiento: padre e hija bastan para el análisis, y un árbol profundo obliga a decidir en cada consulta hasta dónde subir.

El tipo `PATRIMONIO` es lo que separa el consumo real de la compra de bienes. Ver [movimientos](movimientos.md#gasto-contra-patrimonio).

Una instalación nueva arranca con seis cuentas y un catálogo de categorías deliberadamente genéricas ([`Semilla.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Semilla.kt)): describen la naturaleza del rubro, no un banco ni una persona concretos. Están para renombrarse.

### `movimiento`

El libro. Es la única tabla que crece con el uso diario. Índices en `fecha`, `cuentaId`, `categoriaId`, `grupoTransferencia` y `compromisoId`.

| Columna | Notas |
|---|---|
| `fecha` | Día local, guardado como día epoch |
| `importeCentavos` | Centavos **con signo**: negativo sale, positivo entra |
| `cuentaId` | FK con `RESTRICT`: no puede quedar un movimiento sin cuenta |
| `categoriaId` | FK con `SET NULL` |
| `descripcion` | El detalle libre. Complementa a la categoría, no la sustituye |
| `medio` | `EFECTIVO` o `ELECTRONICO` |
| `tipo` | Uno de los seis `TipoMovimiento` |
| `contraparte` | Se deriva del tipo, no se captura |
| `grupoTransferencia` | Une las dos patas de una transferencia; ambas comparten el mismo uuid |
| `compromisoId` | El compromiso que originó el pago, si vino de ahí |
| `nota`, `creadoEn`, `actualizadoEn` | |

### `presupuesto`

Meta por categoría y mes. Índice único por (`categoriaId`, `anio`, `mes`); FK a `categoria` con `CASCADE`.

`montoCentavos` es **siempre positivo**: es un tope de gasto o una meta de ingreso, y compararlo contra el valor absoluto del real evita razonar sobre dos signos a la vez.

### `compromiso`

Lo que ya está comprometido y todavía no se paga.

| Columna | Notas |
|---|---|
| `nombre` | |
| `cuentaId`, `categoriaId` | Admiten nulo: un compromiso puede existir antes de decidirlos |
| `montoCentavos` | Importe de cada pago, positivo |
| `periodicidad` | `MENSUAL`, `BIMESTRAL`, `TRIMESTRAL`, `SEMESTRAL`, `ANUAL` |
| `fechaPrimerPago` | El próximo pago se calcula desde aquí, no se guarda |
| `totalPagos` | `null` = indefinido (una suscripción). Un MSI sí tiene número de pagos |
| `pagosRealizados` | |
| `activo`, `avisarDiasAntes`, `notas` | |

El próximo pago es `fechaPrimerPago + pagosRealizados × meses de la periodicidad`. Al derivarlo, avanzar el plan es incrementar un contador y no reescribir una fecha que podría quedar desfasada.

### `mapeo_descripcion`

Traduce una descripción a su categoría. Es lo que permite que un libro con puras etiquetas planas entre ya clasificado. Índice único por `clave`.

| Columna | Notas |
|---|---|
| `clave` | Descripción normalizada (sin acentos, minúsculas) |
| `categoriaId` | |
| `generadoPorSistema` | Falso cuando lo corrigió el usuario; así no lo pisa una resiembra |

La clave puede llevar sufijo `|TIPO` cuando la misma descripción significa cosas distintas según la dirección: los intereses son ingreso cuando entran y comisión del banco cuando salen.

El mapeo se alimenta de tres fuentes: cada categoría hoja se mapea a sí misma por nombre, los alias explícitos de `Semilla.ALIAS_DESCRIPCION`, y lo que aprende el repositorio cada vez que eliges una categoría para una descripción.

## El dinero y las fechas

**Todo importe es un `Long` de centavos.** En punto flotante un saldo que debería ser cero queda en `999.999999999996`; con enteros el cero es cero y las conciliaciones cuadran. La conversión a decimal solo ocurre al formatear o al escribir la hoja de cálculo ([`Dinero`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/model/Dinero.kt)).

**La fecha se guarda como día epoch**, un entero ordenable y sin zona horaria de por medio ([`Convertidores.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Convertidores.kt)). Las consultas que agrupan por mes lo reconstruyen con `strftime('%Y-%m', fecha * 86400, 'unixepoch')`.

Los enums viajan a SQLite por nombre, no por ordinal: reordenar el enum no debe cambiar el significado de lo ya guardado.

## Invariantes que impone el repositorio

Toda escritura pasa por [`FinanzasRepositorio`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/repo/FinanzasRepositorio.kt):

- **La contraparte siempre se deriva del tipo.** No se recibe de la interfaz.
- **Una transferencia se captura una vez y produce sus dos patas** unidas por el mismo grupo. Al editarla no se actualizan: se borran y se vuelven a escribir dentro de una sola transacción, así el par siempre nace completo aunque lo que hubiera fuera una pata suelta.
- **Borrar una pata borra la otra.** Media transferencia no es un estado válido.
- **El origen y el destino no pueden ser la misma cuenta**, y el importe se captura en positivo.
- **Cada categoría elegida se aprende**: se guarda el par descripción → categoría en `mapeo_descripcion`, salvo en transferencias.
- **El compromiso solo avanza cuando el usuario lo decide.** Guardar un movimiento ligado a un compromiso no sube el contador: cumplir o descartar el pago es un gesto explícito en la lista de compromisos, porque el cargo puede llegar por fuera de la app o no llegar. Cumplir sube `pagosRealizados`; descartar sube `pagosDescartados`. Ambas tienen su inversa (`retrocedeCompromiso`, `restauraPagoCompromiso`) para deshacer el toque.
- **Una reparación de Salud es todo o nada.** [`ReparaDatos`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/usecase/ReparaDatos.kt) no escribe por su cuenta: arma la lista de movimientos corregidos y la manda a `actualizaMovimientos`, que la aplica en una sola transacción. A medias dejaría el libro en un estado que nadie pidió.

### El próximo pago se calcula, no se guarda

`Compromiso` ancla en `fechaPrimerPago` —la fecha del pago número cero, que no se mueve nunca— y expone `proximoPago` sumando hacia adelante `(pagosRealizados + pagosDescartados)` periodicidades. Es la única fórmula: la usan los recordatorios, el tablero, la captura precargada y la hoja de Excel.

El ancla es inmóvil por una razón concreta. `plusMonths` recorta el día al último válido del mes destino y no lo recuerda, así que **encadenar sumas sobre un valor ya recortado arrastra el error**. Antes, descartar un pago movía `fechaPrimerPago`: un plan del 31 de enero pasaba al 28 de febrero, y restaurarlo lo devolvía al 28 de enero en vez de al 31. El día se perdía para siempre y cada descarte volvía a recortarlo. Calculando siempre desde el ancla, el 31 reaparece en cada mes que lo tiene.

Queda una limitación conocida, anotada en el diálogo de edición: si eliges un próximo pago cuyo día no existe en el mes del ancla (un 31 retrocedido a febrero), el día se recorta. No hay ancla que lo evite —ninguna fecha de febrero más un mes cae en un 31 de marzo—; resolverlo pediría guardar el día de pago aparte del ancla.

## Proyecciones

[`Proyecciones.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/Proyecciones.kt) declara lo que devuelven las consultas con join o agregación. Son de solo lectura: nadie las inserta ni las modifica.

| Proyección | Para qué |
|---|---|
| `SaldoCuenta` | Saldo vivo = suma de todos los movimientos de la cuenta |
| `FlujoMes` | Ingresos, gasto de consumo y compra de patrimonio del mes; de ahí salen el neto y la tasa de ahorro |
| `MovimientoDetallado` | Movimiento + nombre de cuenta, nombre de categoría y tipo de cuenta |
| `RenglonPresupuesto` | Meta contra realidad, con desviación y avance |
| `UsoCategoria` | Cuántos movimientos cuelgan de cada categoría; decide si se puede borrar o solo archivar |

## Migraciones

| Versión | Cambio |
|---|---|
| 1 | Esquema inicial |

**No hay ninguna migración: el esquema es el inicial.** Mientras la app no se publique tampoco hará falta ninguna, porque no existe un teléfono ajeno con datos que preservar: si el esquema cambia, se reescribe sobre la versión 1 y se reinstala. El esquema vigente es `app/schemas/1.json`.

Esto deja de valer con la primera versión que instale alguien más. A partir de ahí, cada cambio de esquema necesita su migración: cambia las entidades, sube `version` en [`OllinDatabase`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/OllinDatabase.kt), escribe la `Migration`, regístrala con `addMigrations(...)` y versiona el nuevo `app/schemas/N.json` que genera KSP.

Room **no sabe bajar de versión**: si durante el desarrollo se sube `version` y luego se vuelve atrás, la base que quedó en el teléfono ya no abre. Desinstala la app o borra sus datos; está cifrada, así que no hay forma de rescatarla a mano.

**Nunca uses `fallbackToDestructiveMigration()`.** Es la salida cómoda cuando Room reclama una migración que falta, y lo que hace es borrar la base entera y volver a crearla: en esta app eso es tirar el libro de finanzas del usuario, en silencio y sin posibilidad de deshacer. Si Room reclama, lo que falta es la `Migration`. La prohibición está anotada también en el `databaseBuilder` de [`OllinDatabase`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/db/OllinDatabase.kt), que es donde daría la tentación.

Mientras la app siga sin publicar, cambiar el esquema sobre la versión 1 sí es válido, pero tiene un precio local: la base que ya está en tu teléfono deja de abrir porque su `identityHash` no coincide. Desinstala y vuelve a instalar.
