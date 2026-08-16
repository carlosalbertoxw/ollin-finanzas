# Salud de los datos

Un libro de finanzas se degrada con el uso: se captura de prisa, se importa un archivo viejo, se borra media transferencia. Si los datos están mal, todo lo demás miente — y por eso los hallazgos salen **arriba** de las cifras del tablero y no escondidos en un menú.

[`RevisaCalidad`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/usecase/RevisaCalidad.kt) corre cada vez que se abre el tablero o la pantalla de Salud, no una sola vez al importar.

Corre en `Dispatchers.Default`. Las consultas se van a IO por su cuenta, pero el análisis posterior es CPU pura y una de las revisiones crece con el **cuadrado** de las descripciones distintas: en el hilo del llamador colgaba la interfaz justo al abrir la app, que es cuando el tablero la dispara.

## Los hallazgos

Cada uno lleva clave, gravedad, cuántos movimientos afecta, los ids implicados para poder saltar a ellos, y un `DatosHallazgo` con lo medido: las cuentas citadas, los periodos vacíos, el importe que resume el problema, los pares de descripciones sospechosas.

**El título y el detalle no viven aquí.** Los redacta [`TextosHallazgo`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/TextosHallazgo.kt) en la capa de interfaz, a partir de la clave y de esos datos. El dominio detecta y mide; escribir la frase es material de producto, y separarlo permite corregir una coma sin recompilar un caso de uso ni reescribir sus pruebas —que ahora afirman sobre datos (`datos.cuentas == ["Banorte"]`) y no sobre prosa.

| Clave | Gravedad | Qué detecta | Repara |
|---|---|---|---|
| `tipo_vs_signo` | Alta | La etiqueta del tipo contradice al signo del importe | sí |
| `transferencia_huerfana` | Alta | Patas de transferencia sin grupo, o grupos que no tienen exactamente dos renglones | no |
| `saldo_inicial_duplicado` | Alta | Una cuenta con más de un `BALANCE_INICIAL` | no |
| `saldo_negativo` | Alta | Cuentas que no son de crédito con saldo en rojo | no |
| `sin_categoria` | Media | Movimientos no internos y no transferencia sin categoría | sí |
| `patrimonio_sin_espejo` | Media | Compras de patrimonio registradas como salida, sin cuenta de activo que las reciba | no |
| `meses_vacios` | Media | Huecos de meses completos entre el primer y el último movimiento | no |
| `medio_incoherente` | Baja | Efectivo capturado como electrónico y viceversa | sí |
| `descripciones_parecidas` | Baja | Dos formas de escribir lo mismo (Levenshtein ≤ 2) | no |

Los hallazgos salen ordenados por gravedad.

### Por qué algunos no se reparan solos

- **Saldos iniciales duplicados** — sumarlos y borrar uno son correcciones distintas según si fue un duplicado o dos capturas parciales, y solo el usuario sabe cuál fue. El detalle dice cuánto vale el renglón que sobra, porque ese desvío se arrastra al patrimonio y a los meses de colchón.
- **Transferencias huérfanas** — falta información: normalmente la cuenta que no está registrada. Hay que abrir cada una y completarla.
- **Descripciones parecidas** — decidir que "Gasolina" y "Gasolna" son lo mismo es del usuario; la app solo enseña los pares sospechosos antes de que se vuelvan dos rubros distintos.

### `medio_incoherente`, con matiz

Lo único que vale para cualquier cuenta es que **el dinero de una cartera se mueve en mano**. Lo demás lo decide la propia cuenta con su bandera `soloElectronico`, no su tipo: un préstamo familiar comparte tipo con la tarjeta pero sí puede recibir efectivo, y atarlo al tipo haría que la revisión marcara como error un dato correcto.

## Las reparaciones

[`ReparaDatos`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/domain/usecase/ReparaDatos.kt) arregla los hallazgos con corrección inequívoca. Devuelve cuántos renglones tocó.

**Regla de oro: nunca se toca el importe.** El importe es lo que realmente pasó y de él dependen todos los saldos; lo que se corrige es la etiqueta que lo describe mal.

**Una reparación es todo o nada.** No escribe renglón por renglón: arma la lista completa de movimientos corregidos y la manda a `FinanzasRepositorio.actualizaMovimientos`, que la aplica dentro de una sola transacción. Antes mandaba un `UPDATE` suelto por movimiento contra los DAO, así que un tropiezo a media reparación dejaba el libro mitad corregido —y era el único punto de la app que se saltaba la puerta única de escritura.

| Clave | Qué hace |
|---|---|
| `tipo_vs_signo` | Voltea el tipo para que coincida con el signo: entrada ↔ salida, transferencia entrada ↔ salida. Los internos no se tocan: admiten cualquier signo |
| `medio_incoherente` | Pone el medio que la cuenta impone. Sin regla declarada no se toca: lo que capturaste manda |
| `sin_categoria` | Asigna la categoría **ya aprendida** para esa misma descripción, si existe y sigue viva |

`sin_categoria` clasifica solo lo que se puede clasificar sin adivinar. Adivinar una categoría es peor que no tenerla: para el resto está la lista de revisión uno por uno.

## La pantalla de revisión

Desde Salud de los datos, cada hallazgo abre `revision/{clave}` con **la lista concreta** de movimientos detrás del aviso ([`RevisionPantalla`](../app/src/main/java/com/carlosalbertoxw/ollin/finanzas/ui/screens/RevisionPantalla.kt)). El aviso dice cuántos están mal; aquí se ve cuáles.

- Cuando lo único que falta es la categoría, se elige desde la misma lista sin abrir el movimiento. Solo se ofrecen las categorías hoja que corresponden al signo del importe: gasto y patrimonio si sale dinero, ingreso si entra.
- El resto abre la captura del movimiento.
- La revisión se repite al volver (`ON_RESUME`) sin apagar la lista, así lo corregido desaparece solo.
