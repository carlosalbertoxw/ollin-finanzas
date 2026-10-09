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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Periodicidad
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.components.FilaDeslizable
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaCifra
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * La lista partida en los dos estados que de verdad se miran distinto.
 *
 * [archivados] son los que ya no piden nada: el plan a plazos que llego a su
 * ultima mensualidad y el pago unico que se cumplio o se descarto. Siguen
 * existiendo -- son historia, y borrarlos es otra decision -- pero estorban
 * arriba, donde uno viene a ver que debe.
 */
data class ListaCompromisos(
    val activos: List<Compromiso> = emptyList(),
    val archivados: List<Compromiso> = emptyList()
) {
    val vacia: Boolean get() = activos.isEmpty() && archivados.isEmpty()
}
class CompromisosVm(private val repo: FinanzasRepositorio) : ViewModel() {

    /**
     * Lo urgente arriba. El orden lo manda el proximo pago, que no es una
     * columna sino la fecha del primero corrida por los pagos ya hechos, asi
     * que se ordena aqui: en SQL, cumplir uno lo dejaria en su lugar viejo.
     */
    val compromisos: StateFlow<ListaCompromisos> = repo.observaCompromisos()
        .map { lista ->
            val (activos, archivados) = lista.partition { it.activo }
            ListaCompromisos(
                activos = activos.sortedWith(
                    compareBy<Compromiso> { it.proximoPago }.thenBy { it.nombre.lowercase() }
                ),
                // Al reves que los activos: en lo cerrado lo que se busca es lo
                // ultimo que se cerro, no lo mas viejo del archivo.
                archivados = archivados.sortedWith(
                    compareByDescending<Compromiso> { it.proximoPago }
                        .thenBy { it.nombre.lowercase() }
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListaCompromisos())

    val cuentas: StateFlow<List<Cuenta>> = repo.observaCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solo las hojas: son las que se capturan, igual que en la pantalla de captura. */
    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .map { lista -> lista.filter { it.padreId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(compromiso: Compromiso) {
        viewModelScope.launch { repo.guardaCompromiso(compromiso) }
    }

    fun elimina(compromiso: Compromiso) {
        viewModelScope.launch { repo.eliminaCompromiso(compromiso) }
    }

    // El plan no avanza solo. Un pago sigue pendiente hasta que aqui se decide
    // que se cumplio o que se descarta, porque el cargo puede llegar por fuera
    // de la app, rebotar o simplemente no cobrarse este periodo.

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
}
