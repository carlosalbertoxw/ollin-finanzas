# El libro: movimientos, saldos y patrimonio

Las reglas con las que Ollin Finanzas interpreta un renglón. Todo lo demás —tableros, analítica, presupuesto, el libro de Excel— sale de aquí.

## El dinero son centavos

Todo importe vive como **centavos en un `Long`** ([`Dinero`](../app/src/main/java/mx/ollin/finanzas/domain/model/Dinero.kt)). Guardar dinero en punto flotante deja saldos como `999.999999999996` o `-6.25e-13` donde debería haber un cero exacto; con enteros el cero es cero y las conciliaciones cuadran. La conversión a decimal solo pasa al formatear en pantalla o al escribir una celda.

`Dinero.parsea` acepta lo que la gente teclea de verdad: `1,234.56`, `1234,56`, `$1 234.56`, el signo al frente y los paréntesis contables `(455.33)`. Cuando hay coma y punto, manda el último como separador decimal; con solo comas, es decimal si deja uno o dos dígitos a la derecha.

## El signo lo dice todo

**El importe lleva signo: negativo sale, positivo entra.** No hay columna aparte de "es gasto"; el saldo de una cuenta es literalmente `SUM(importeCentavos)` de sus movimientos, sin casos especiales que mantener.

## Los seis tipos

[`TipoMovimiento`](../app/src/main/java/mx/ollin/finanzas/domain/model/Enums.kt) son seis y solo seis: cualquier renglón cae en uno.

| Tipo | Signo esperado | Qué es |
|---|---|---|
| `ENTRADA` | + | Dinero que llega de un tercero |
| `SALIDA` | − | Dinero que se va a un tercero |
| `TRANSFERENCIA_ENTRADA` | + | Pata que recibe, entre cuentas tuyas |
| `TRANSFERENCIA_SALIDA` | − | Pata que envía, entre cuentas tuyas |
| `BALANCE_INICIAL` | cualquiera | El saldo con el que arranca la cuenta |
| `AJUSTE_VALOR` | cualquiera | El bien cambió de valor sin que se moviera un peso |

Los dos últimos son **internos**: no llevan categoría ni cruzan contra nadie. Y admiten cualquier signo a propósito: una tarjeta puede arrancar debiendo, y un activo puede revaluarse o depreciarse.

El `signoEsperado` no es decorativo: es lo que permite detectar y reparar los renglones donde la etiqueta contradice al importe. Ver [calidad](calidad.md#tipo-y-signo-se-contradicen).

### El ajuste de valor guarda la diferencia

Al capturar un ajuste escribes **cuánto vale hoy** el bien, que es lo único que su dueño tiene en la cabeza. Lo que se guarda es la **diferencia** contra el saldo actual, no el valor: así el saldo sigue siendo la suma de los movimientos, sin excepciones que mantener aparte. Al editar un ajuste, la pantalla vuelve a mostrar el valor resultante y no la diferencia.

## La contraparte se deriva, no se captura

[`Contraparte`](../app/src/main/java/mx/ollin/finanzas/domain/model/Enums.kt) dice si el movimiento ocurre entre cuentas tuyas (`1 - PROPIA`) o con alguien más (`2 - TERCERO`). No se pregunta: se deduce del tipo.

```
transferencia o interno  →  PROPIA
entrada o salida         →  TERCERO
```

Un campo que se captura a mano es un campo que se deja de mantener, y entonces todo reporte que dependa de él miente. El repositorio lo normaliza en cada escritura y el importador lo recalcula al entrar.

## Transferencias: una captura, dos renglones

Mover dinero entre tus cuentas se captura **una vez**, con importe positivo, origen y destino. El repositorio escribe las dos patas ligadas por un mismo `grupoTransferencia`:

- Origen: importe negativo, `TRANSFERENCIA_SALIDA`, medio por defecto de la cuenta origen.
- Destino: importe positivo, `TRANSFERENCIA_ENTRADA`, medio por defecto de la cuenta destino.

Borrar una borra la otra. Editar no actualiza: borra el grupo y lo vuelve a escribir dentro de una transacción, así el par siempre nace completo aunque lo que hubiera fuera una pata suelta importada de un archivo.

La categoría por omisión es "Transferencia entre cuentas", pero se respeta la que traiga: una compra de patrimonio viaja como transferencia y conserva su categoría ("Inmuebles").

**Pagar la tarjeta con la nómina es una transferencia, no un gasto**: el gasto ya se registró cuando compraste.

## Gasto contra patrimonio

Comprar un terreno, cripto o un celular caro **no es consumo**: es trasladar patrimonio de una cuenta a otra. Ollin Finanzas lo separa en dos ejes que se apoyan:

- **Categorías de tipo `PATRIMONIO`** — quedan fuera del gasto de consumo en tableros, analítica y en la hoja de Ingresos - Egresos, donde salen en su propio bloque.
- **Cuentas de tipo `ACTIVO`** — guardan valor en vez de dinero líquido.

Al capturar con naturaleza *Patrimonio*, la pantalla pide la cuenta de Activo a la que entra el dinero y escribe el movimiento **como transferencia ligada**, no como salida. Por eso la tasa de ahorro no se desploma el mes que compraste algo grande.

Si la compra se registró como salida y no existe la cuenta espejo, la revisión de calidad lo señala: el patrimonio queda subestimado.

## Las cinco naturalezas de la captura

[`NaturalezaCaptura`](../app/src/main/java/mx/ollin/finanzas/ui/screens/CapturaPantalla.kt) es lo que se elige en pantalla, y no coincide con `TipoMovimiento` porque *gasto* y *compra de patrimonio* terminan siendo cosas distintas en el modelo aunque las dos saquen dinero.

| Naturaleza | Qué se escribe |
|---|---|
| Gasto | `SALIDA` con importe negativo |
| Ingreso | `ENTRADA` con importe positivo |
| Patrimonio | Transferencia ligada hacia la cuenta de Activo |
| Saldo inicial | `BALANCE_INICIAL`; en contra si la cuenta es de deuda |
| Ajuste de valor | `AJUSTE_VALOR` con la diferencia contra el saldo actual |

Reglas de la captura:

- **Las listas son cerradas.** Cuenta y categoría se eligen, no se escriben: así "Super" y "super " no acaban siendo dos rubros distintos.
- **Solo se ofrecen las categorías hoja que corresponden** a la naturaleza elegida. Cambiar de naturaleza limpia la categoría, porque no se cruzan.
- **El medio sigue a la cuenta**: al elegirla se toma su `medioPorDefecto`, que es lo que evita marcar la cartera como electrónica.
- **El saldo inicial avisa si ya existía uno** para esa cuenta, antes de duplicarlo.
- La opción de saldo inicial se puede apagar en Ajustes: se ocupa al dar de alta una cuenta y después estorba.

## Cuentas: liquidez, deuda y patrimonio no líquido

[`TipoCuenta`](../app/src/main/java/mx/ollin/finanzas/domain/model/Enums.kt) decide cómo entra cada saldo al balance.

| Tipo | Deuda | Líquida |
|---|---|---|
| Efectivo | no | sí |
| Cuenta de banco | no | sí |
| Inversión | no | sí |
| Créditos - préstamos | sí | no |
| Créditos - MSI | sí | no |
| Activo / patrimonio | no | no |

De ahí salen las cifras del tablero:

```
patrimonio neto = liquidez + deuda (negativa) + patrimonio no líquido
```

Las cuentas marcadas como **fuera del patrimonio** no entran a ninguna cifra agregada: sirven para el dinero que pasa por tus manos sin ser tuyo.

## Colchón y tasa de ahorro

- **Gasto mensual promedio** — promedio del gasto de *consumo* por mes, **descartando el mes en curso**: un mes a medias jala el promedio hacia abajo y regala meses de colchón que no existen.
- **Meses de colchón** = liquidez ÷ gasto mensual promedio. Solo cuenta la liquidez: el patrimonio no líquido no paga la despensa.
- **Tasa de ahorro** = (ingresos + gasto de consumo) ÷ ingresos, con el gasto en negativo. Se mide contra el consumo real, que es el que refleja tu tren de vida.

Las dos son `null` mientras no haya contra qué medirlas. Un "0.0 meses" o un "0%" ahí no dirían "no tienes colchón", dirían "no hay datos", y son cosas opuestas para quien lee.

## Presupuesto

Meta por categoría y mes, siempre positiva, comparada contra el valor absoluto de lo real. La desviación negativa significa que te pasaste, y la barra pasa a ámbar sobre el 85% y a rojo al 100%.

Las metas de un mes se pueden copiar al siguiente, que es como se arma un presupuesto real.

## Compromisos

Lo que ya está comprometido y aún no se paga: mensualidades MSI, suscripciones, gastos anuales. Cada uno lleva cuenta, categoría, periodicidad, monto y fecha del primer pago.

- El **próximo pago** se calcula: `fechaPrimerPago + pagosRealizados × meses`. Es también lo que ordena la lista —activos primero, lo más atrasado hasta arriba—, y por eso el orden se arma en el ViewModel y no en SQL: cumplir un pago no toca `fechaPrimerPago`, así que ordenar por columna dejaría la tarjeta recién cumplida en su lugar viejo con la fecha nueva.
- Un compromiso con `totalPagos` termina solo: al llegar al último, se apaga.
- **Registrar no da el pago por hecho.** Abre la captura ya llena —cuenta, categoría, monto, medio y naturaleza deducida del tipo de la categoría— para que corrijas lo que haya cambiado. Guardar escribe el movimiento y lo deja ligado al compromiso, pero no mueve el plan.
- **El plan avanza a mano.** Se desliza la tarjeta a la derecha y aparecen dos decisiones:
  - **Cumplir** — sube `pagosRealizados` y apaga el plan si con ese pago se acabó.
  - **Descartar** — recorre `fechaPrimerPago` una periodicidad sin subir el contador: el mes que no se cobró no acorta un MSI.
  - Las dos se deshacen desde el aviso que aparece abajo.
- **Mientras nadie decida, el pago sigue pendiente**, aunque ya se haya pasado de fecha: sale marcado como vencido en la lista y en el tablero, y el recordatorio diario lo sigue nombrando. Es a propósito: el cargo puede llegar por fuera de la app, rebotar o no cobrarse este periodo, y solo el dueño de la cuenta sabe cuál de las tres pasó.
- Editar el pago de un compromiso no toca el plan: solo conserva el vínculo.

Una revisión diaria a las 9:00 avisa de lo que ya entró en su ventana (`avisarDiasAntes`, 3 por omisión) y de lo que se venció sin resolverse. El tablero usa la misma función con una ventana de 45 días para enseñar lo que viene. Ver [seguridad](seguridad.md#permisos).
