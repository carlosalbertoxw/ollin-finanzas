# Modelo de datos

Room sobre SQLite cifrado. Seis tablas, versión de esquema **2**, esquemas exportados en `app/schemas/`.

Las entidades de Room son también el modelo de dominio: [`Entidades.kt`](../app/src/main/java/mx/ollin/finanzas/data/db/Entidades.kt).

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

Una instalación nueva arranca con seis cuentas y un catálogo de categorías deliberadamente genéricas ([`Semilla.kt`](../app/src/main/java/mx/ollin/finanzas/data/db/Semilla.kt)): describen la naturaleza del rubro, no un banco ni una persona concretos. Están para renombrarse.

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

**Todo importe es un `Long` de centavos.** En punto flotante un saldo que debería ser cero queda en `999.999999999996`; con enteros el cero es cero y las conciliaciones cuadran. La conversión a decimal solo ocurre al formatear o al escribir la hoja de cálculo ([`Dinero`](../app/src/main/java/mx/ollin/finanzas/domain/model/Dinero.kt)).

**La fecha se guarda como día epoch**, un entero ordenable y sin zona horaria de por medio ([`Convertidores.kt`](../app/src/main/java/mx/ollin/finanzas/data/db/Convertidores.kt)). Las consultas que agrupan por mes lo reconstruyen con `strftime('%Y-%m', fecha * 86400, 'unixepoch')`.

Los enums viajan a SQLite por nombre, no por ordinal: reordenar el enum no debe cambiar el significado de lo ya guardado.

## Invariantes que impone el repositorio

Toda escritura pasa por [`FinanzasRepositorio`](../app/src/main/java/mx/ollin/finanzas/data/repo/FinanzasRepositorio.kt):

- **La contraparte siempre se deriva del tipo.** No se recibe de la interfaz.
- **Una transferencia se captura una vez y produce sus dos patas** unidas por el mismo grupo. Al editarla no se actualizan: se borran y se vuelven a escribir dentro de una sola transacción, así el par siempre nace completo aunque lo que hubiera fuera una pata suelta.
- **Borrar una pata borra la otra.** Media transferencia no es un estado válido.
- **El origen y el destino no pueden ser la misma cuenta**, y el importe se captura en positivo.
- **Cada categoría elegida se aprende**: se guarda el par descripción → categoría en `mapeo_descripcion`, salvo en transferencias.
- **El compromiso solo avanza cuando el usuario lo decide.** Guardar un movimiento ligado a un compromiso no sube el contador: cumplir o descartar el pago es un gesto explícito en la lista de compromisos, porque el cargo puede llegar por fuera de la app o no llegar. Cumplir sube `pagosRealizados`; descartar recorre `fechaPrimerPago` una periodicidad. Ambas tienen su inversa (`retrocedeCompromiso`, `restauraPagoCompromiso`) para deshacer el toque.

## Proyecciones

[`Proyecciones.kt`](../app/src/main/java/mx/ollin/finanzas/data/db/Proyecciones.kt) declara lo que devuelven las consultas con join o agregación. Son de solo lectura: nadie las inserta ni las modifica.

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

**No hay ninguna migración, y es a propósito.** La app todavía no se ha publicado, así que no existe ni un teléfono con datos que preservar. Durante el desarrollo llegó a haber una versión 2 que retiraba la columna `tipo` de `compromiso`; al no haber usuarios, se plegó sobre la 1 en vez de arrastrar una migración que nadie iba a ejecutar. El esquema de `app/schemas/1.json` es el resultado, ya sin esa columna.

Esto deja de valer con la primera versión que instale alguien más. A partir de ahí, cada cambio de esquema necesita su migración: cambia las entidades, sube `version` en [`OllinDatabase`](../app/src/main/java/mx/ollin/finanzas/data/db/OllinDatabase.kt), escribe la `Migration`, regístrala con `addMigrations(...)` y versiona el nuevo `app/schemas/N.json` que genera KSP.

Y una advertencia mientras tanto: si tienes la app instalada de antes, su archivo quedó marcado como versión 2 y Room **no sabe bajar de versión**. Al abrir con la versión 1 truena. Desinstala o borra los datos de la app; la base está cifrada, así que tampoco hay forma de rescatarla a mano.
