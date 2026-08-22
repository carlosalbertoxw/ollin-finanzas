package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.FlujoMes
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDetallado
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.GravedadHallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.Hallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad
import com.carlosalbertoxw.ollin.finanzas.ui.components.BarrasFlujo
import com.carlosalbertoxw.ollin.finanzas.ui.components.LineaEvolucion
import com.carlosalbertoxw.ollin.finanzas.ui.components.Punto
import com.carlosalbertoxw.ollin.finanzas.ui.components.AlturaMinimaDeslizable
import com.carlosalbertoxw.ollin.finanzas.ui.components.FilaDeslizable
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaCifra
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaValor
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.titulo
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
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

class TableroVm(
    private val repo: FinanzasRepositorio,
    private val ajustes: AjustesRepositorio,
    private val revisaCalidad: RevisaCalidad
) : ViewModel() {

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

    // Las mismas decisiones que ofrece la lista de Compromisos. Se repiten aqui
    // porque el tablero es donde de verdad se ven los pagos que vienen, y
    // mandar a la persona a otra pantalla para dos toques sobra.

    fun cumple(id: Long) {
        viewModelScope.launch { repo.avanzaCompromiso(id) }
    }

    fun deshaceCumplimiento(id: Long) {
        viewModelScope.launch { repo.retrocedeCompromiso(id) }
    }

    fun descarta(id: Long) {
        viewModelScope.launch { repo.descartaPagoCompromiso(id) }
    }

    fun deshaceDescarte(id: Long) {
        viewModelScope.launch { repo.restauraPagoCompromiso(id) }
    }

    /** Manda si el tablero enseña o no sus atajos de ayuda. */
    val muestraTutoriales: StateFlow<Boolean> = ajustes.ajustes
        .map { it.muestraTutoriales }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        revisaCalidad()
    }

    fun revisaCalidad() {
        viewModelScope.launch {
            hallazgos.value = runCatching { revisaCalidad.ejecuta() }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun TableroPantalla(
    repo: FinanzasRepositorio,
    ajustes: AjustesRepositorio,
    revisaCalidad: RevisaCalidad,
    alAbrirCuentas: () -> Unit,
    alAbrirCalidad: () -> Unit,
    alAbrirCompromisos: () -> Unit,
    alPagarCompromiso: (Long) -> Unit,
    alAbrirAjustes: () -> Unit,
    alAbrirTutoriales: () -> Unit
) {
    val vm = recuerdaVm("tablero") { TableroVm(repo, ajustes, revisaCalidad) }
    val estado by vm.estado.collectAsStateWithLifecycle()
    val muestraTutoriales by vm.muestraTutoriales.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()

    /** Cumplir y descartar se deshacen: son decisiones de un toque sobre datos reales. */
    fun avisa(texto: String, alDeshacer: () -> Unit) {
        alcance.launch {
            avisos.currentSnackbarData?.dismiss()
            val respuesta = avisos.showSnackbar(
                message = texto,
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short
            )
            if (respuesta == SnackbarResult.ActionPerformed) alDeshacer()
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                Row {
                    if (muestraTutoriales) {
                        IconButton(onClick = alAbrirTutoriales) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Tutoriales"
                            )
                        }
                    }
                    IconButton(onClick = alAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            }
        }

        // La tarjeta grande solo mientras el libro esta en blanco: quien ya captura
        // a diario no necesita que le expliquen cada vez que abre la app.
        if (muestraTutoriales && estado.saldos.none { it.movimientos > 0 }) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = alAbrirTutoriales),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Empieza por aqui",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Da de alta tus cuentas y captura tu primer movimiento, " +
                                    "paso a paso.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
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
                                estado.hallazgos.first().titulo(),
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
                // Mismo gesto que en la lista de Compromisos: tocar abre la
                // captura ya llena, deslizar descubre cumplir y descartar. Que
                // signifique lo mismo en los dos lugares es la mitad del valor.
                FilaDeslizable(
                    habilitada = true,
                    alCumplir = {
                        vm.cumple(compromiso.id)
                        avisa("${compromiso.nombre}: pago cumplido") {
                            vm.deshaceCumplimiento(compromiso.id)
                        }
                    },
                    alDescartar = {
                        vm.descarta(compromiso.id)
                        avisa("${compromiso.nombre}: pago descartado") {
                            vm.deshaceDescarte(compromiso.id)
                        }
                    }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Los dos requisitos de FilaDeslizable. El alto
                            // minimo es para que "Cumplir" y "Descartar" quepan
                            // con su texto: el panel hereda la altura de esta
                            // fila, y mas bajo solo se veian los iconos.
                            .heightIn(min = AlturaMinimaDeslizable)
                            .background(MaterialTheme.colorScheme.background)
                            .clickable { alPagarCompromiso(compromiso.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Los atrasados no se caen de la lista: siguen aqui,
                        // marcados, hasta que se cumplan o se descarten.
                        val vencido = fecha.isBefore(LocalDate.now())
                        Column(Modifier.weight(1f)) {
                            Text(compromiso.nombre, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (vencido) "Vencido el ${Recordatorios.formateaFecha(fecha)}"
                                else Recordatorios.formateaFecha(fecha)) +
                                    "  ·  ${compromiso.periodicidad.etiqueta}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (vencido) colores.alerta else colores.textoTenue
                            )
                        }
                        TextoDinero(-compromiso.montoCentavos)
                    }
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

        // Sobre la barra de pestañas, no debajo: ahi es donde el aviso se ve y
        // donde "Deshacer" queda al alcance del pulgar.
        SnackbarHost(
            avisos,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
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
