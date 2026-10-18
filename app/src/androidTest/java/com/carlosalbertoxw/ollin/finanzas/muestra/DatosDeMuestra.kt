package com.carlosalbertoxw.ollin.finanzas.muestra

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.finanzas.OllinApp
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.Presupuesto
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Llena la app `.debug` del telefono con tres meses y medio de un libro
 * inventado, para sacar las capturas de la ficha de Play sin ensenar las
 * finanzas de nadie.
 *
 * Escribe en la base de verdad, no en una en memoria como las demas pruebas, y
 * antes la vacia. Por eso no corre si no se le pide con el argumento
 * `muestra=si`: la suite completa pasa de largo por aqui y no se lleva los
 * datos de quien la corre en su telefono. Ver docs/publicacion.md.
 *
 * La semilla del azar es fija, asi que dos cargas el mismo dia dan lo mismo.
 */
@RunWith(AndroidJUnit4::class)
class DatosDeMuestra {

    private val contenedor = ApplicationProvider.getApplicationContext<OllinApp>().contenedor
    private val repo = contenedor.repositorio
    private val azar = Random(SEMILLA)
    private val hoy = LocalDate.now()

    private lateinit var cuentas: Map<String, Cuenta>
    private lateinit var categorias: Map<String, Long>
    private val gastoConTarjeta = mutableMapOf<YearMonth, Double>()

    @Test
    fun carga() = runBlocking {
        val argumentos = InstrumentationRegistry.getArguments()
        // Sin el argumento termina sin tocar nada. No es un assume: el informe
        // de Gradle cuenta la suposicion fallida como prueba fallida.
        if (argumentos.getString("muestra") != "si") return@runBlocking

        // El arranque de la app siembra el catalogo en segundo plano; se espera
        // a que la cuenta deje de moverse para no vaciar la base a medio
        // sembrar. Sembrar desde aqui a la vez chocaria con los nombres unicos.
        val categoriaDao = contenedor.baseDeDatos.categoriaDao()
        val limite = System.currentTimeMillis() + ESPERA_SIEMBRA_MS
        var antes = -1
        while (System.currentTimeMillis() < limite) {
            val cuenta = categoriaDao.cuenta()
            if (cuenta > 0 && cuenta == antes) break
            antes = cuenta
            Thread.sleep(PAUSA_MS)
        }
        contenedor.baseDeDatos.clearAllTables()
        contenedor.sembrador.sembrarSiHaceFalta()

        contenedor.ajustes.quitaBloqueo()
        contenedor.ajustes.guardaTema(true)
        contenedor.ajustes.guardaMuestraTutoriales(false)
        contenedor.ajustes.guardaRespaldoHecho(System.currentTimeMillis())

        cuentas = repo.listaCuentas().associateBy { it.nombre }
        val vivienda = repo.listaCategorias().first { it.nombre == "Vivienda" }
        repo.guardaCategoria(
            Categoria(
                nombre = "Renta",
                padreId = vivienda.id,
                tipo = TipoCategoria.GASTO,
                esencial = true
            )
        )
        categorias = repo.listaCategorias().associate { it.nombre to it.id }

        val desde = hoy.minusMonths(MESES_DE_HISTORIA).withDayOfMonth(1)
        saldoInicial(desde, BANCO, 15_000.0)
        saldoInicial(desde, EFECTIVO, 1_800.0)
        saldoInicial(desde, INVERSION, 40_000.0)
        saldoInicial(desde, TARJETA, -5_400.0)
        saldoInicial(desde, MSI, -4_800.0)
        saldoInicial(desde, PATRIMONIO, 180_000.0, "Auto")

        var dia = desde
        while (!dia.isAfter(hoy)) {
            unDia(dia)
            dia = dia.plusDays(1)
        }

        compromisos(desde)
        presupuestos()
    }

    private suspend fun unDia(dia: LocalDate) {
        val d = dia.dayOfMonth
        when (d) {
            1 -> salida(dia, BANCO, "Renta", "Renta del departamento", 9_800.0)

            2 -> transferencia(dia, BANCO, INVERSION, "Ahorro del mes", 5_000.0)

            3 -> transferencia(
                dia,
                BANCO,
                EFECTIVO,
                "Retiro de efectivo",
                3_500.0,
                "Retiro de efectivo"
            )

            5 -> salida(dia, BANCO, "Membresías", "Gimnasio", 650.0)

            8 -> transferencia(
                dia,
                BANCO,
                TARJETA,
                "Pago de tarjeta",
                // Se liquida lo que se cargo el mes anterior, como quien no
                // paga intereses; el primer mes, el saldo con que arranco.
                gastoConTarjeta[YearMonth.from(dia).minusMonths(1)] ?: 5_400.0,
                "Pago de tarjeta"
            )

            9 -> salida(dia, TARJETA, "Ropa", "Ropa", pesos(600, 1_400))

            10 -> salida(dia, BANCO, "Internet", "Internet de casa", 549.0)

            12 -> salida(dia, TARJETA, "Streaming", "Streaming de música", 129.0)

            14 -> salida(dia, TARJETA, "Alimento para mascota", "Croquetas", 780.0)

            16 -> salida(dia, EFECTIVO, "Peluquería", "Corte de cabello", 250.0)

            18 -> salida(dia, EFECTIVO, "Medicamentos", "Farmacia", pesos(150, 450))

            20 -> salida(dia, BANCO, "Teléfono", "Plan de celular", 399.0)

            22 -> salida(dia, TARJETA, "Streaming", "Streaming de video", 219.0)

            25 -> transferencia(dia, BANCO, MSI, "Tableta a 12 MSI", 400.0, "Pago de tarjeta")

            28 -> entrada(dia, INVERSION, "Intereses", "Rendimiento del fondo", pesos(470, 560))
        }
        if (d == 15 || d == dia.lengthOfMonth()) entrada(dia, BANCO, "Salario", "Nómina", 18_500.0)
        if (dia == hoy.minusMonths(1).withDayOfMonth(20)) {
            entrada(dia, BANCO, "Honorarios", "Proyecto freelance", 4_000.0)
        }

        when (dia.dayOfWeek) {
            DayOfWeek.SATURDAY -> salida(
                dia,
                BANCO,
                "Despensa",
                "Supermercado",
                pesos(1_200, 2_100)
            )

            DayOfWeek.WEDNESDAY -> salida(
                dia,
                EFECTIVO,
                "Despensa",
                "Frutas y verduras",
                pesos(250, 450)
            )

            else -> Unit
        }
        if (dia.dayOfWeek == DayOfWeek.FRIDAY || dia.dayOfWeek == DayOfWeek.SATURDAY) {
            if (azar.nextInt(100) < 65) {
                salida(dia, TARJETA, "Salidas", SALIDAS.random(azar), pesos(150, 650))
            }
        }
        if (dia.dayOfWeek.value <= 5 && azar.nextInt(100) < 30) {
            salida(dia, EFECTIVO, "Salidas", "Comida en la oficina", pesos(120, 220))
        }
        if (d % 10 == 4) salida(dia, TARJETA, "Gasolina", "Gasolina", pesos(850, 1_050))
        if (d % 14 == 6) salida(dia, BANCO, "Transporte privado", "Taxi", pesos(90, 180))
    }

    private suspend fun compromisos(desde: LocalDate) {
        val meses = (hoy.year - desde.year) * 12 + hoy.monthValue - desde.monthValue

        // Los pagos que ya ocurrieron en la historia de arriba, para que el
        // proximo de cada uno caiga despues de hoy.
        fun realizados(diaDelMes: Int) = meses + if (hoy.dayOfMonth >= diaDelMes) 1 else 0

        val planes = listOf(
            Triple("Renta del departamento", 1, Pair("Renta", 9_800.0)),
            Triple("Gimnasio", 5, Pair("Membresías", 650.0)),
            Triple("Internet de casa", 10, Pair("Internet", 549.0)),
            Triple("Plan de celular", 20, Pair("Teléfono", 399.0)),
            Triple("Streaming de video", 22, Pair("Streaming", 219.0))
        )
        planes.forEach { (nombre, diaDelMes, categoriaYMonto) ->
            val (categoria, monto) = categoriaYMonto
            repo.guardaCompromiso(
                Compromiso(
                    nombre = nombre,
                    cuentaId = cuentas.getValue(
                        if (categoria ==
                            "Streaming"
                        ) {
                            TARJETA
                        } else {
                            BANCO
                        }
                    ).id,
                    categoriaId = categorias[categoria],
                    montoCentavos = centavos(monto),
                    fechaPrimerPago = desde.withDayOfMonth(diaDelMes),
                    pagosRealizados = realizados(diaDelMes)
                )
            )
        }
        repo.guardaCompromiso(
            Compromiso(
                nombre = "Tableta a 12 MSI",
                cuentaId = cuentas.getValue(MSI).id,
                categoriaId = categorias["Equipo de cómputo"],
                montoCentavos = centavos(400.0),
                fechaPrimerPago = desde.withDayOfMonth(25),
                totalPagos = 12,
                pagosRealizados = realizados(25)
            )
        )
        repo.guardaCompromiso(
            Compromiso(
                nombre = "Seguro del auto",
                cuentaId = cuentas.getValue(BANCO).id,
                categoriaId = categorias["Seguro de auto"],
                montoCentavos = centavos(8_900.0),
                periodicidad = Periodicidad.ANUAL,
                fechaPrimerPago = hoy.plusMonths(3).withDayOfMonth(15),
                avisarDiasAntes = 15
            )
        )
    }

    private suspend fun presupuestos() {
        val topes = mapOf(
            "Renta" to 9_800.0,
            "Despensa" to 7_500.0,
            "Salidas" to 3_500.0,
            "Gasolina" to 2_800.0,
            "Ropa" to 1_200.0,
            "Streaming" to 400.0,
            "Membresías" to 650.0,
            "Medicamentos" to 500.0
        )
        topes.forEach { (categoria, tope) ->
            repo.guardaPresupuesto(
                Presupuesto(
                    categoriaId = categorias.getValue(categoria),
                    anio = hoy.year,
                    mes = hoy.monthValue,
                    montoCentavos = centavos(tope)
                )
            )
        }
    }

    private suspend fun saldoInicial(
        fecha: LocalDate,
        cuenta: String,
        pesos: Double,
        descripcion: String = "Saldo inicial"
    ) {
        val destino = cuentas.getValue(cuenta)
        repo.guardaMovimiento(
            Movimiento(
                fecha = fecha,
                importeCentavos = centavos(pesos),
                cuentaId = destino.id,
                descripcion = descripcion,
                medio = destino.medioPorDefecto,
                tipo = TipoMovimiento.BALANCE_INICIAL,
                contraparte = Contraparte.PROPIA
            )
        )
    }

    private suspend fun entrada(
        fecha: LocalDate,
        cuenta: String,
        categoria: String,
        descripcion: String,
        pesos: Double
    ) = movimiento(fecha, cuenta, categoria, descripcion, centavos(pesos), TipoMovimiento.ENTRADA)

    private suspend fun salida(
        fecha: LocalDate,
        cuenta: String,
        categoria: String,
        descripcion: String,
        pesos: Double
    ) {
        if (cuenta == TARJETA) {
            gastoConTarjeta.merge(YearMonth.from(fecha), pesos, Double::plus)
        }
        movimiento(fecha, cuenta, categoria, descripcion, -centavos(pesos), TipoMovimiento.SALIDA)
    }

    private suspend fun movimiento(
        fecha: LocalDate,
        cuenta: String,
        categoria: String,
        descripcion: String,
        importe: Long,
        tipo: TipoMovimiento
    ) {
        val origen = cuentas.getValue(cuenta)
        repo.guardaMovimiento(
            Movimiento(
                fecha = fecha,
                importeCentavos = importe,
                cuentaId = origen.id,
                categoriaId = categorias.getValue(categoria),
                descripcion = descripcion,
                medio = origen.medioPorDefecto,
                tipo = tipo,
                contraparte = Contraparte.TERCERO
            )
        )
    }

    private suspend fun transferencia(
        fecha: LocalDate,
        origen: String,
        destino: String,
        descripcion: String,
        pesos: Double,
        categoria: String = "Transferencia entre cuentas"
    ) = repo.guardaTransferencia(
        fecha = fecha,
        importeCentavos = centavos(pesos),
        cuentaOrigenId = cuentas.getValue(origen).id,
        cuentaDestinoId = cuentas.getValue(destino).id,
        descripcion = descripcion,
        categoriaId = categorias[categoria]
    )

    /** Un importe al azar entre dos cifras, con centavos redondos a 50. */
    private fun pesos(minimo: Int, maximo: Int): Double =
        azar.nextInt(minimo * 2, maximo * 2 + 1) / 2.0

    private fun centavos(pesos: Double): Long = Math.round(pesos * 100)

    private companion object {
        const val SEMILLA = 2026
        const val MESES_DE_HISTORIA = 3L
        const val ESPERA_SIEMBRA_MS = 10_000L
        const val PAUSA_MS = 300L

        const val EFECTIVO = "Efectivo"
        const val BANCO = "Cuenta de banco"
        const val INVERSION = "Inversión"
        const val TARJETA = "Créditos - préstamos"
        const val MSI = "Créditos - MSI"
        const val PATRIMONIO = "Patrimonio"

        val SALIDAS = listOf("Cena con amigos", "Cine", "Café", "Tacos", "Concierto")
    }
}
