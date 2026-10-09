package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.FlujoMes
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDetallado
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.data.notify.AvisoDeRespaldo
import com.carlosalbertoxw.ollin.finanzas.data.notify.Recordatorios
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.GravedadHallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.Hallazgo
import com.carlosalbertoxw.ollin.finanzas.domain.usecase.RevisaCalidad
import com.carlosalbertoxw.ollin.finanzas.ui.components.AlturaMinimaDeslizable
import com.carlosalbertoxw.ollin.finanzas.ui.components.BarrasFlujo
import com.carlosalbertoxw.ollin.finanzas.ui.components.FilaDeslizable
import com.carlosalbertoxw.ollin.finanzas.ui.components.LineaEvolucion
import com.carlosalbertoxw.ollin.finanzas.ui.components.Punto
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaCifra
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaValor
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.finanzas.ui.titulo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class EstadoTablero(
    val saldos: List<SaldoCuenta> = emptyList(),
    val flujo: List<FlujoMes> = emptyList(),
    val proximos: List<Pair<Compromiso, LocalDate>> = emptyList(),
    val hallazgos: List<Hallazgo> = emptyList(),
    /**
     * Si ya llego algo de la base. El estado inicial no trae cuentas, y sin
     * esta marca "todavia no se leyo" y "el libro esta vacio" se ven igual.
     */
    val cargado: Boolean = false
) {
    /**
     * La tarjeta de "Empieza por aqui": solo con el libro en blanco de verdad.
     *
     * Hasta la 1.1.0 se decidia sobre el estado inicial, que no tiene cuentas
     * porque la base cifrada aun no termino de abrir: la tarjeta salia un
     * instante al entrar y desaparecia en cuanto llegaban los datos. Por eso
     * exige [cargado], y el interruptor de los tutoriales ya leido del disco
     * (nulo mientras no).
     */
    fun muestraBienvenida(muestraTutoriales: Boolean?): Boolean =
        cargado && muestraTutoriales == true && saldos.none { it.movimientos > 0 }

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
    private val revisaCalidad: RevisaCalidad,
    private val avisoDeRespaldo: AvisoDeRespaldo
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
            hallazgos = problemas,
            cargado = true
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

    /**
     * El texto del aviso de respaldo, o nulo si no toca.
     *
     * La hora se toma al combinar. No hace falta un reloj que avise: al irse
     * la app al fondo se deja de escuchar, y al volver se recalcula con la hora
     * de ese momento. Exportar escribe el ultimo respaldo en DataStore, y eso
     * basta para que el aviso desaparezca solo.
     */
    val avisoRespaldo: StateFlow<String?> = combine(
        ajustes.ajustes,
        avisoDeRespaldo.descartado
    ) { preferencias, descartado ->
        AvisoDeRespaldo.texto(preferencias, descartado, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun descartaAvisoRespaldo() = avisoDeRespaldo.descarta()

    /**
     * Manda si el tablero enseña o no sus atajos de ayuda. Nulo mientras no se
     * lee del disco: partir de `true` dibujaba la ayuda un instante a quien la
     * tiene apagada.
     */
    val muestraTutoriales: StateFlow<Boolean?> = ajustes.ajustes
        .map<_, Boolean?> { it.muestraTutoriales }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        revisaCalidad()
    }

    fun revisaCalidad() {
        viewModelScope.launch {
            hallazgos.value = runCatching { revisaCalidad.ejecuta() }.getOrDefault(emptyList())
        }
    }
}
