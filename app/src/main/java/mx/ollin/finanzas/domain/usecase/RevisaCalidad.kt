package mx.ollin.finanzas.domain.usecase

import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.data.db.Movimiento
import mx.ollin.finanzas.data.repo.FinanzasRepositorio
import mx.ollin.finanzas.domain.model.Medio
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoCuenta
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.domain.model.normalizaClave
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.min

enum class GravedadHallazgo { ALTA, MEDIA, BAJA }

data class Hallazgo(
    val clave: String,
    val titulo: String,
    val detalle: String,
    val gravedad: GravedadHallazgo,
    val afectados: Int,
    /** Movimientos implicados, para poder saltar a ellos desde la pantalla. */
    val idsMovimiento: List<Long> = emptyList(),
    val reparable: Boolean = false
)

/**
 * Auditoria continua de los datos: corre cada vez que abres la pantalla de
 * Salud, no una sola vez, porque un libro de finanzas se degrada con el uso.
 */
class RevisaCalidad(private val repo: FinanzasRepositorio) {

    suspend fun ejecuta(): List<Hallazgo> {
        val movimientos = repo.datosParaExportar().movimientos
        if (movimientos.isEmpty()) return emptyList()

        val cuentas = repo.listaCuentas().associateBy { it.id }
        val categorias = repo.listaCategorias().associateBy { it.id }

        return buildList {
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
            clave = "tipo_vs_signo",
            titulo = "Tipo y signo se contradicen",
            detalle = "Hay movimientos marcados como entrada con importe negativo, o al reves. " +
                "Los saldos salen bien pero cualquier reporte por tipo queda mal.",
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
            clave = "transferencia_huerfana",
            titulo = "Transferencias sin su contraparte",
            detalle = "Una transferencia debe mover dinero de una cuenta a otra, con dos renglones " +
                "que se cancelan. Estas quedaron a medias, asi que el patrimonio total esta desviado. " +
                "Abre cada una y completa la cuenta que falta.",
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
        val nombres = repetidos.keys.joinToString { cuentas[it]?.nombre ?: "cuenta $it" }
        val inflado = repetidos.values.sumOf { porCuenta ->
            porCuenta.sumOf { it.importeCentavos } - porCuenta.maxBy { abs(it.importeCentavos) }.importeCentavos
        }
        return Hallazgo(
            clave = "saldo_inicial_duplicado",
            titulo = "Cuentas con mas de un saldo inicial",
            detalle = "Una cuenta arranca una sola vez, pero estas tienen varios y todos se " +
                "suman: $nombres. Los renglones de mas valen " +
                "${mx.ollin.finanzas.domain.model.Dinero.formatea(inflado)}, y ese desvio se " +
                "arrastra a tu patrimonio y a los meses de colchon. Abrelos y borra el que sobre.",
            gravedad = GravedadHallazgo.ALTA,
            afectados = implicados.size,
            idsMovimiento = implicados.map { it.id }
        )
    }

    private fun sinCategoria(movimientos: List<Movimiento>): Hallazgo? {
        val huerfanos = movimientos.filter {
            it.categoriaId == null && !it.tipo.esInterno && !it.tipo.esTransferencia
        }
        if (huerfanos.isEmpty()) return null
        val monto = huerfanos.sumOf { abs(it.importeCentavos) }
        return Hallazgo(
            clave = "sin_categoria",
            titulo = "Movimientos sin categoria",
            detalle = "Suman ${mx.ollin.finanzas.domain.model.Dinero.formatea(monto)} que no aparecen " +
                "en ningun analisis por categoria. Corregir automaticamente clasifica los que " +
                "repiten una descripcion que ya usaste antes; el resto se revisa uno por uno.",
            gravedad = GravedadHallazgo.MEDIA,
            afectados = huerfanos.size,
            idsMovimiento = huerfanos.map { it.id },
            reparable = true
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
            clave = "medio_incoherente",
            titulo = "Medio que no cuadra con la cuenta",
            detalle = "Una salida de la cartera no puede ser electronica, ni una cuenta marcada " +
                "como solo electronica puede recibir efectivo.",
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
        val hayCuentaActivo = cuentas.values.any { it.tipo == TipoCuenta.ACTIVO }
        val monto = compras.sumOf { abs(it.importeCentavos) }
        return Hallazgo(
            clave = "patrimonio_sin_espejo",
            titulo = "Compras de patrimonio contadas como gasto",
            detalle = "${mx.ollin.finanzas.domain.model.Dinero.formatea(monto)} en terreno, cripto o " +
                "bienes duraderos salieron de tus cuentas pero no entraron a ninguna cuenta de activo" +
                if (hayCuentaActivo) ". Registralas como transferencia hacia la cuenta de activo."
                else ". Crea una cuenta de tipo Activo para reflejarlas.",
            gravedad = GravedadHallazgo.MEDIA,
            afectados = compras.size,
            idsMovimiento = compras.map { it.id }
        )
    }

    /** Dos formas de escribir lo mismo, antes de que se vuelvan dos categorias distintas. */
    private fun descripcionesParecidas(movimientos: List<Movimiento>): Hallazgo? {
        val distintas = movimientos.map { it.descripcion }.distinct().filter { it.length >= 5 }
        val sospechosas = mutableListOf<Pair<String, String>>()
        for (i in distintas.indices) {
            for (j in i + 1 until distintas.size) {
                val a = distintas[i].normalizaClave()
                val b = distintas[j].normalizaClave()
                if (a == b) continue
                if (abs(a.length - b.length) > 2) continue
                if (distancia(a, b) <= 2) sospechosas += distintas[i] to distintas[j]
            }
        }
        if (sospechosas.isEmpty()) return null
        val ejemplos = sospechosas.take(3).joinToString("; ") { "\"${it.first}\" / \"${it.second}\"" }
        return Hallazgo(
            clave = "descripciones_parecidas",
            titulo = "Descripciones casi identicas",
            detalle = "Probablemente sean la misma cosa escrita de dos formas, y el gasto se te " +
                "esta partiendo en dos renglones: $ejemplos",
            gravedad = GravedadHallazgo.BAJA,
            afectados = sospechosas.size
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
            clave = "meses_vacios",
            titulo = "Meses sin ningun movimiento",
            detalle = "No hay registros en ${faltantes.joinToString(", ")}. O no capturaste, " +
                "o falta importar un periodo.",
            gravedad = GravedadHallazgo.MEDIA,
            afectados = faltantes.size
        )
    }

    private fun saldoNegativoEnCuentaDeActivo(
        movimientos: List<Movimiento>,
        cuentas: Map<Long, Cuenta>
    ): Hallazgo? {
        val negativas = cuentas.values.filter { cuenta ->
            !cuenta.tipo.esDeuda &&
                movimientos.filter { it.cuentaId == cuenta.id }.sumOf { it.importeCentavos } < 0
        }
        if (negativas.isEmpty()) return null
        return Hallazgo(
            clave = "saldo_negativo",
            titulo = "Cuentas que no son de credito con saldo negativo",
            detalle = "Estas cuentas quedaron en rojo: ${negativas.joinToString { it.nombre }}. " +
                "Suele faltar el saldo inicial o sobrar una salida.",
            gravedad = GravedadHallazgo.ALTA,
            afectados = negativas.size
        )
    }

    /** Levenshtein acotado; suficiente para detectar erratas de captura. */
    private fun distancia(a: String, b: String): Int {
        if (a == b) return 0
        val anterior = IntArray(b.length + 1) { it }
        val actual = IntArray(b.length + 1)
        for (i in 1..a.length) {
            actual[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = min(min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo)
            }
            System.arraycopy(actual, 0, anterior, 0, actual.size)
        }
        return anterior[b.length]
    }
}
