package mx.ollin.finanzas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.FlujoMes
import mx.ollin.finanzas.data.db.MovimientoDetallado
import mx.ollin.finanzas.data.db.SaldoCuenta
import mx.ollin.finanzas.data.notify.Recordatorios
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.usecase.GravedadHallazgo
import mx.ollin.finanzas.domain.usecase.Hallazgo
import mx.ollin.finanzas.ui.components.BarrasFlujo
import mx.ollin.finanzas.ui.components.LineaEvolucion
import mx.ollin.finanzas.ui.components.Punto
import mx.ollin.finanzas.ui.components.SeccionTitulo
import mx.ollin.finanzas.ui.components.TarjetaCifra
import mx.ollin.finanzas.ui.components.TarjetaValor
import mx.ollin.finanzas.ui.components.TextoDinero
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
import java.time.LocalDate
import kotlin.math.abs

data class EstadoTablero(
    val saldos: List<SaldoCuenta> = emptyList(),
    val flujo: List<FlujoMes> = emptyList(),
    val proximos: List<Pair<Compromiso, LocalDate>> = emptyList(),
    val hallazgos: List<Hallazgo> = emptyList()
) {
    /**
     * Las cuentas marcadas como fuera del patrimonio no entran a ninguna cifra
     * agregada. Sirven para llevar el registro de dinero que pasa por tus manos
     * pero no es tuyo, sin que infle tu patrimonio ni tu colchon.
     */
    private val propias: List<SaldoCuenta> get() = saldos.filter { it.incluirEnPatrimonio }

    val liquidez: Long get() = propias.filter { it.tipo.esLiquida }.sumOf { it.saldoCentavos }
    val deuda: Long get() = propias.filter { it.tipo.esDeuda }.sumOf { it.saldoCentavos }
    val noLiquido: Long
        get() = propias.filter { !it.tipo.esLiquida && !it.tipo.esDeuda }.sumOf { it.saldoCentavos }
    val patrimonio: Long get() = liquidez + deuda + noLiquido

    /** El ultimo mes casi siempre esta a medias, asi que no entra al promedio. */
    val gastoMensualPromedio: Long
        get() {
            val considerados = if (flujo.size > 1) flujo.dropLast(1) else flujo
            if (considerados.isEmpty()) return 0L
            return considerados.sumOf { abs(it.gastoConsumoCentavos) } / considerados.size
        }

    /**
     * null mientras no haya gasto registrado. Un "0.0 meses" ahi seria mentira:
     * no dice que no tengas colchon, dice que no hay contra que medirlo, y son
     * cosas opuestas para quien lo lee.
     */
    val mesesDeColchon: Double?
        get() = if (gastoMensualPromedio <= 0L) null else liquidez.toDouble() / gastoMensualPromedio

    /** null sin ingresos registrados; un 0% se leeria como "no ahorras nada". */
    val tasaAhorroPromedio: Double?
        get() {
            val ingresos = flujo.sumOf { it.ingresosCentavos }
            if (ingresos <= 0L) return null
            return (ingresos + flujo.sumOf { it.gastoConsumoCentavos }).toDouble() / ingresos
        }

    val patrimonioAcumulado: List<Long>
        get() {
            var acumulado = 0L
            return flujo.map { acumulado += it.netoCentavos; acumulado }
        }
}

class TableroVm(private val contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio
    private val hallazgos = MutableStateFlow<List<Hallazgo>>(emptyList())

    val estado: StateFlow<EstadoTablero> = combine(
        repo.observaSaldos(),
        repo.observaFlujoMensual(),
        repo.observaCompromisos(),
        hallazgos.asStateFlow()
    ) { saldos, flujo, compromisos, problemas ->
        EstadoTablero(
            saldos = saldos,
            flujo = flujo,
            proximos = Recordatorios.porVencer(compromisos.map { it.copy(avisarDiasAntes = 45) }),
            hallazgos = problemas
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoTablero())

    init {
        revisaCalidad()
    }

    fun revisaCalidad() {
        viewModelScope.launch {
            hallazgos.value = runCatching { contenedor.revisaCalidad.ejecuta() }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun TableroPantalla(
    contenedor: Contenedor,
    alAbrirCuentas: () -> Unit,
    alAbrirCalidad: () -> Unit,
    alAbrirCompromisos: () -> Unit,
    alAbrirAjustes: () -> Unit
) {
    val vm = recuerdaVm("tablero") { TableroVm(contenedor) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Patrimonio neto", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
                    Text(
                        Dinero.formatea(estado.patrimonio),
                        style = MaterialTheme.typography.displaySmall
                    )
                }
                IconButton(onClick = alAbrirAjustes) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }

        if (estado.patrimonioAcumulado.size >= 2) {
            item {
                LineaEvolucion(
                    valores = estado.patrimonioAcumulado,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    color = colores.entrada
                )
            }
        }

        // Los hallazgos van arriba: si los datos estan mal, todo lo demas miente.
        if (estado.hallazgos.isNotEmpty()) {
            item {
                val graves = estado.hallazgos.count { it.gravedad == GravedadHallazgo.ALTA }
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = alAbrirCalidad),
                    colors = CardDefaults.cardColors(
                        containerColor = if (graves > 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${estado.hallazgos.size} cosas que revisar",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                estado.hallazgos.first().titulo,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaCifra(
                    etiqueta = "Liquidez",
                    centavos = estado.liquidez,
                    modifier = Modifier.weight(1f),
                    acento = colores.entrada
                )
                TarjetaCifra(
                    etiqueta = "Deuda",
                    centavos = estado.deuda,
                    modifier = Modifier.weight(1f),
                    acento = colores.salida
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tasa = estado.tasaAhorroPromedio
                TarjetaValor(
                    etiqueta = "Tasa de ahorro",
                    valor = if (tasa == null) "Sin datos" else "%.0f%%".format(tasa * 100),
                    nota = if (tasa == null) "Sin ingresos aun" else "sobre consumo real",
                    modifier = Modifier.weight(1f),
                    color = if (tasa == null) colores.textoTenue else colores.entrada
                )

                val meses = estado.mesesDeColchon
                TarjetaValor(
                    etiqueta = "Fondo de emergencia",
                    valor = if (meses == null) "Sin datos" else "%.1f meses".format(meses),
                    nota = if (meses == null) "Sin gastos aun"
                    else Dinero.formateaCompacto(estado.gastoMensualPromedio) + "/mes",
                    modifier = Modifier.weight(1f),
                    // Gris y no ambar: que no se pueda medir no es una mala noticia.
                    color = when {
                        meses == null -> colores.textoTenue
                        meses >= 6 -> colores.entrada
                        else -> colores.alerta
                    }
                )
            }
        }

        if (estado.flujo.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SeccionTitulo("Ingresos contra gasto")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "La compra de patrimonio no cuenta como gasto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                        Spacer(Modifier.height(12.dp))
                        val ultimos = estado.flujo.takeLast(6)
                        BarrasFlujo(
                            ingresos = ultimos.map { it.ingresosCentavos },
                            gastos = ultimos.map { it.gastoConsumoCentavos },
                            etiquetas = ultimos.map { it.periodo.takeLast(2) }
                        )
                    }
                }
            }
        }

        if (estado.proximos.isNotEmpty()) {
            item {
                SeccionTitulo("Se viene") {
                    TextButton(onClick = alAbrirCompromisos) { Text("Ver todo") }
                }
            }
            // La clave lleva prefijo porque este LazyColumn mezcla secciones de
            // tablas distintas: el compromiso 1 y la cuenta 1 chocarian.
            items(estado.proximos.take(4), key = { "compromiso-${it.first.id}" }) { (compromiso, fecha) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(compromiso.nombre, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "$fecha  ·  ${compromiso.periodicidad.etiqueta}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                    TextoDinero(-compromiso.montoCentavos)
                }
            }
        }

        item {
            SeccionTitulo("Tus cuentas") {
                TextButton(onClick = alAbrirCuentas) { Text("Administrar") }
            }
        }
        items(estado.saldos.filter { it.movimientos > 0 }, key = { "cuenta-${it.cuentaId}" }) { saldo ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(saldo.nombre, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        saldo.tipo.etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                TextoDinero(saldo.saldoCentavos, coloreado = saldo.saldoCentavos < 0)
            }
        }
    }
}

/** Renglon compartido por el tablero y la lista de movimientos. */
@Composable
fun RenglonMovimiento(
    detalle: MovimientoDetallado,
    alTocar: () -> Unit
) {
    val colores = LocalColoresOllin.current
    val m = detalle.movimiento
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = alTocar)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Punto(
            color = when {
                m.tipo.esTransferencia -> colores.traspaso
                m.importeCentavos >= 0 -> colores.entrada
                else -> colores.salida
            },
            tamano = 8
        )
        Column(Modifier.weight(1f)) {
            Text(
                m.descripcion,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                buildString {
                    append(m.fecha.toString())
                    append("  ·  ")
                    append(detalle.nombreCuenta)
                    detalle.nombreCategoria?.let { append("  ·  $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextoDinero(m.importeCentavos)
    }
}
