package com.carlosalbertoxw.ollin.finanzas.domain.usecase

import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.domain.model.normalizaClave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

enum class GravedadHallazgo { ALTA, MEDIA, BAJA }

/** Claves estables de los hallazgos. Las usan la navegacion y [ReparaDatos]. */
object ClaveHallazgo {
    const val TIPO_VS_SIGNO = "tipo_vs_signo"
    const val TRANSFERENCIA_HUERFANA = "transferencia_huerfana"
    const val SALDO_INICIAL_DUPLICADO = "saldo_inicial_duplicado"
    const val SIN_CATEGORIA = "sin_categoria"
    const val MEDIO_INCOHERENTE = "medio_incoherente"
    const val PATRIMONIO_SIN_ESPEJO = "patrimonio_sin_espejo"
    const val DESCRIPCIONES_PARECIDAS = "descripciones_parecidas"
    const val MESES_VACIOS = "meses_vacios"
    const val SALDO_NEGATIVO = "saldo_negativo"
}

/**
 * Lo que hace falta para redactar el hallazgo, ya medido pero todavia sin
 * frase. El dominio detecta y cuenta; escribir el texto que lee el usuario es
 * trabajo de la interfaz, que es donde se puede cambiar una coma sin recompilar
 * un caso de uso ni romper sus pruebas.
 */
data class DatosHallazgo(
    /** Nombres de cuenta citados por el hallazgo. */
    val cuentas: List<String> = emptyList(),
    /** Periodos "yyyy-MM" implicados. */
    val periodos: List<String> = emptyList(),
    /** Pares de descripciones que se parecen entre si. */
    val ejemplos: List<Pair<String, String>> = emptyList(),
    /** Importe que resume el hallazgo, en centavos. */
    val montoCentavos: Long? = null,
    /** Si ya existe alguna cuenta de tipo Activo donde reflejar el patrimonio. */
    val hayCuentaDeActivo: Boolean = false
)

data class Hallazgo(
    val clave: String,
    val gravedad: GravedadHallazgo,
    val afectados: Int,
    /** Movimientos implicados, para poder saltar a ellos desde la pantalla. */
    val idsMovimiento: List<Long> = emptyList(),
    val reparable: Boolean = false,
    val datos: DatosHallazgo = DatosHallazgo()
)

/**
 * Auditoria continua de los datos: corre cada vez que abres la pantalla de
 * Salud, no una sola vez, porque un libro de finanzas se degrada con el uso.
 */
class RevisaCalidad(private val repo: FinanzasRepositorio) {

    /**
     * En [Dispatchers.Default] a proposito. Las consultas se van a IO por su
     * cuenta, pero el analisis posterior es CPU pura y crece con el cuadrado de
     * las descripciones distintas; en el hilo del llamador colgaria la
     * interfaz, porque quien mas lo llama es el tablero al abrir la app.
     */
    suspend fun ejecuta(): List<Hallazgo> = withContext(Dispatchers.Default) {
        val movimientos = repo.listaMovimientos()
        if (movimientos.isEmpty()) return@withContext emptyList()

        val cuentas = repo.listaCuentas().associateBy { it.id }
        val categorias = repo.listaCategorias().associateBy { it.id }

        buildList {
            tipoContraSigno(movimientos)?.let(::add)
            transferenciasHuerfanas(movimientos)?.let(::add)
            saldosInicialesDuplicados(movimientos, cuentas)?.let(::add)
            sinCategoria(movimientos)?.let(::add)
            medioIncoherente(movimientos, cuentas)?.let(::add)
            patrimonioSinCuentaEspejo(movimientos, categorias, cuentas)?.let(::add)
            descripcionesParecidas(movimientos)?.let(::add)
            mesesSinRegistros(movimientos)?.let(::add)
            saldoNegativoEnCuentaDeActivo(movimientos, cuentas)?.let(::add)
        }.sortedBy { it.gravedad.ordinal }
    }

    /** La etiqueta del tipo contradice al signo del importe. */
    private fun tipoContraSigno(movimientos: List<Movimiento>): Hallazgo? {
        val malos = movimientos.filter { m ->
            val esperado = m.tipo.signoEsperado
            esperado != 0 && m.importeCentavos != 0L &&
                (if (m.importeCentavos > 0) 1 else -1) != esperado
        }
        if (malos.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.TIPO_VS_SIGNO,
            gravedad = GravedadHallazgo.ALTA,
            afectados = malos.size,
            idsMovimiento = malos.map { it.id },
            reparable = true
        )
    }

    private fun transferenciasHuerfanas(movimientos: List<Movimiento>): Hallazgo? {
        val patas = movimientos.filter { it.tipo.esTransferencia }
        val sueltas = patas.filter { it.grupoTransferencia == null } +
            patas.filter { it.grupoTransferencia != null }
                .groupBy { it.grupoTransferencia }
                .filterValues { it.size != 2 }
                .values.flatten()
        if (sueltas.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.TRANSFERENCIA_HUERFANA,
            gravedad = GravedadHallazgo.ALTA,
            afectados = sueltas.size,
            idsMovimiento = sueltas.map { it.id }
        )
    }

    /**
     * Una cuenta arranca una sola vez. Si tiene dos saldos iniciales los dos se
     * suman, y como el saldo es la base de todo lo demas (patrimonio, liquidez,
     * meses de colchon), el error se propaga a cada cifra sin dejar rastro.
     *
     * No se repara solo: sumarlos y borrar uno son correcciones distintas segun
     * si fue un duplicado o dos capturas parciales, y solo tu sabes cual fue.
     */
    private fun saldosInicialesDuplicados(
        movimientos: List<Movimiento>,
        cuentas: Map<Long, Cuenta>
    ): Hallazgo? {
        val repetidos = movimientos
            .filter { it.tipo == TipoMovimiento.BALANCE_INICIAL }
            .groupBy { it.cuentaId }
            .filterValues { it.size > 1 }
        if (repetidos.isEmpty()) return null

        val implicados = repetidos.values.flatten()
        val inflado = repetidos.values.sumOf { porCuenta ->
            val mayor = porCuenta.maxBy { abs(it.importeCentavos) }
            porCuenta.sumOf { it.importeCentavos } - mayor.importeCentavos
        }
        return Hallazgo(
            clave = ClaveHallazgo.SALDO_INICIAL_DUPLICADO,
            gravedad = GravedadHallazgo.ALTA,
            afectados = implicados.size,
            idsMovimiento = implicados.map { it.id },
            datos = DatosHallazgo(
                cuentas = repetidos.keys.map { cuentas[it]?.nombre ?: "cuenta $it" },
                montoCentavos = inflado
            )
        )
    }

    private fun sinCategoria(movimientos: List<Movimiento>): Hallazgo? {
        val huerfanos = movimientos.filter {
            it.categoriaId == null && !it.tipo.esInterno && !it.tipo.esTransferencia
        }
        if (huerfanos.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.SIN_CATEGORIA,
            gravedad = GravedadHallazgo.MEDIA,
            afectados = huerfanos.size,
            idsMovimiento = huerfanos.map { it.id },
            reparable = true,
            datos = DatosHallazgo(montoCentavos = huerfanos.sumOf { abs(it.importeCentavos) })
        )
    }

    /** Efectivo capturado como electronico y viceversa. */
    private fun medioIncoherente(
        movimientos: List<Movimiento>,
        cuentas: Map<Long, Cuenta>
    ): Hallazgo? {
        val malos = movimientos.filter { m ->
            val cuenta = cuentas[m.cuentaId] ?: return@filter false
            when {
                // Lo unico que vale para cualquier cuenta: el dinero de una
                // cartera se mueve en mano. Lo demas lo decide cada cuenta.
                cuenta.tipo == TipoCuenta.EFECTIVO -> m.medio != Medio.EFECTIVO
                cuenta.soloElectronico -> m.medio != Medio.ELECTRONICO
                else -> false
            }
        }
        if (malos.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.MEDIO_INCOHERENTE,
            gravedad = GravedadHallazgo.BAJA,
            afectados = malos.size,
            idsMovimiento = malos.map { it.id },
            reparable = true
        )
    }

    /**
     * Comprar terreno o cripto no es gastar: es mover patrimonio de una cuenta a
     * otra. Si no existe la cuenta espejo, el patrimonio queda subestimado.
     */
    private fun patrimonioSinCuentaEspejo(
        movimientos: List<Movimiento>,
        categorias: Map<Long, Categoria>,
        cuentas: Map<Long, Cuenta>
    ): Hallazgo? {
        val compras = movimientos.filter {
            it.tipo == TipoMovimiento.SALIDA &&
                categorias[it.categoriaId]?.tipo == TipoCategoria.PATRIMONIO
        }
        if (compras.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.PATRIMONIO_SIN_ESPEJO,
            gravedad = GravedadHallazgo.MEDIA,
            afectados = compras.size,
            idsMovimiento = compras.map { it.id },
            datos = DatosHallazgo(
                montoCentavos = compras.sumOf { abs(it.importeCentavos) },
                hayCuentaDeActivo = cuentas.values.any { it.tipo == TipoCuenta.ACTIVO }
            )
        )
    }

    /**
     * Dos formas de escribir lo mismo, antes de que se vuelvan dos categorias
     * distintas.
     *
     * La clave normalizada se calcula una sola vez por descripcion y no dentro
     * del bucle: son n comparaciones de longitud contra n^2 pares, y normalizar
     * ahi dentro repetia el mismo trabajo millones de veces.
     */
    private fun descripcionesParecidas(movimientos: List<Movimiento>): Hallazgo? {
        val distintas = movimientos.map { it.descripcion }.distinct().filter { it.length >= 5 }
        val claves = distintas.map { it.normalizaClave() }

        val sospechosas = mutableListOf<Pair<String, String>>()
        for (i in distintas.indices) {
            val a = claves[i]
            for (j in i + 1 until distintas.size) {
                val b = claves[j]
                if (a == b) continue
                if (abs(a.length - b.length) > 2) continue
                if (distanciaHasta(a, b, 2) <= 2) sospechosas += distintas[i] to distintas[j]
            }
        }
        if (sospechosas.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.DESCRIPCIONES_PARECIDAS,
            gravedad = GravedadHallazgo.BAJA,
            afectados = sospechosas.size,
            datos = DatosHallazgo(ejemplos = sospechosas.take(3))
        )
    }

    private fun mesesSinRegistros(movimientos: List<Movimiento>): Hallazgo? {
        val meses = movimientos.map { YearMonth.from(it.fecha) }.distinct().sorted()
        if (meses.size < 2) return null
        val faltantes = mutableListOf<YearMonth>()
        var cursor = meses.first()
        while (cursor < meses.last()) {
            cursor = cursor.plusMonths(1)
            if (cursor !in meses && cursor < meses.last()) faltantes += cursor
        }
        if (faltantes.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.MESES_VACIOS,
            gravedad = GravedadHallazgo.MEDIA,
            afectados = faltantes.size,
            datos = DatosHallazgo(periodos = faltantes.map { it.toString() })
        )
    }

    /**
     * Los saldos se acumulan en una sola pasada sobre los movimientos.
     * Recorrer la lista entera una vez por cuenta daria lo mismo multiplicando
     * el trabajo por el numero de cuentas.
     */
    private fun saldoNegativoEnCuentaDeActivo(
        movimientos: List<Movimiento>,
        cuentas: Map<Long, Cuenta>
    ): Hallazgo? {
        val saldos = HashMap<Long, Long>(cuentas.size)
        for (m in movimientos) saldos[m.cuentaId] = (saldos[m.cuentaId] ?: 0L) + m.importeCentavos

        val negativas = cuentas.values.filter { cuenta ->
            !cuenta.tipo.esDeuda && (saldos[cuenta.id] ?: 0L) < 0
        }
        if (negativas.isEmpty()) return null
        return Hallazgo(
            clave = ClaveHallazgo.SALDO_NEGATIVO,
            gravedad = GravedadHallazgo.ALTA,
            afectados = negativas.size,
            datos = DatosHallazgo(cuentas = negativas.map { it.nombre })
        )
    }
}

/**
 * Distancia de Levenshtein entre [a] y [b], o `tope + 1` si pasa de [tope].
 *
 * La revision de descripciones solo pregunta si dos textos estan a 2 o menos,
 * y compara cada descripcion contra todas las demas: con cinco mil distintas
 * son doce millones de pares. Calcular la tabla entera para cada uno costaba
 * mas de treinta segundos en una computadora (ver RendimientoTest), y corre
 * cada vez que se abre el tablero.
 *
 * Dos atajos que no cambian la respuesta:
 * - **Solo la franja diagonal.** Una celda a mas de [tope] de la diagonal ya
 *   cuesta mas de [tope] ediciones, asi que se trata como "demasiado lejos".
 * - **Corte temprano.** Las celdas de una fila nunca bajan en la siguiente; si
 *   toda la fila ya paso de [tope], el resultado tambien.
 *
 * Interna para poder compararla en una prueba contra la tabla completa.
 */
internal fun distanciaHasta(a: String, b: String, tope: Int): Int {
    val lejos = tope + 1
    if (a == b) return 0
    if (abs(a.length - b.length) > tope) return lejos

    var anterior = IntArray(b.length + 1) { if (it <= tope) it else lejos }
    var actual = IntArray(b.length + 1)
    for (i in 1..a.length) {
        actual.fill(lejos)
        actual[0] = if (i <= tope) i else lejos
        var menorDeLaFila = actual[0]
        for (j in maxOf(1, i - tope)..minOf(b.length, i + tope)) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            val celda = minOf(actual[j - 1] + 1, anterior[j] + 1, anterior[j - 1] + costo)
            actual[j] = min(celda, lejos)
            if (actual[j] < menorDeLaFila) menorDeLaFila = actual[j]
        }
        if (menorDeLaFila > tope) return lejos
        val previo = anterior
        anterior = actual
        actual = previo
    }
    return anterior[b.length]
}
