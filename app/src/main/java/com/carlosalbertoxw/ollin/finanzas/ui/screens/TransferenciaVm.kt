package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Capturar o corregir una transferencia como una sola cosa. La app genera los
 * dos renglones (salida y entrada) unidos por el mismo grupo, de modo que no
 * exista la posibilidad de dejar media transferencia y desviar el patrimonio.
 *
 * Al abrirla desde una pata existente se carga el par completo; al guardar se
 * reescribe entero, que es lo que permite reparar tambien las que quedaron a
 * medias.
 */
class TransferenciaVm(
    private val repo: FinanzasRepositorio,
    private val movimientoId: Long?
) : ViewModel() {

    var fecha by mutableStateOf(LocalDate.now())
    var importeTexto by mutableStateOf("")
    var origenId by mutableStateOf<Long?>(null)
    var destinoId by mutableStateOf<Long?>(null)
    var descripcion by mutableStateOf("Transferencia entre cuentas")
    var error by mutableStateOf<String?>(null)
        private set
    var cargado by mutableStateOf(movimientoId == null)
        private set

    /** Cierto cuando lo que se abrio no era un par sano: falta una pata o sobran. */
    var aMedias by mutableStateOf(false)
        private set

    val esEdicion: Boolean get() = movimientoId != null

    /** Grupo que se reemplaza al guardar; null cuando la pata estaba suelta. */
    private var grupo: String? = null
    private var idsSueltos: List<Long> = emptyList()
    private var nota: String? = null

    /** Se conserva tal cual: una compra de patrimonio viaja con su propia categoria. */
    private var categoriaId: Long? = null

    val cuentas: StateFlow<List<Cuenta>> = repo.observaCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _cerrar = MutableStateFlow(false)
    val cerrar: StateFlow<Boolean> = _cerrar

    init {
        if (movimientoId != null) viewModelScope.launch { carga(movimientoId) }
    }

    private suspend fun carga(id: Long) {
        val pata = repo.movimiento(id)
        if (pata == null) { cargado = true; return }

        grupo = pata.grupoTransferencia
        val patas = grupo?.let { repo.movimientosDeGrupo(it) }?.takeIf { it.isNotEmpty() }
            ?: listOf(pata)
        val salida = patas.firstOrNull { it.importeCentavos < 0 }
        val entrada = patas.firstOrNull { it.importeCentavos > 0 }
        val referencia = salida ?: entrada ?: pata

        fecha = referencia.fecha
        importeTexto = Dinero.aTextoHoja(kotlin.math.abs(referencia.importeCentavos))
        origenId = salida?.cuentaId
        destinoId = entrada?.cuentaId
        descripcion = referencia.descripcion
        nota = referencia.nota
        categoriaId = referencia.categoriaId
        // Sin grupo no hay nada que borrar por grupo: se borran por id.
        idsSueltos = if (grupo == null) patas.map { it.id } else emptyList()
        aMedias = salida == null || entrada == null || patas.size != 2
        cargado = true
    }

    fun guarda() {
        val centavos = Dinero.parsea(importeTexto)?.let { kotlin.math.abs(it) }
        val origen = origenId
        val destino = destinoId
        when {
            centavos == null || centavos == 0L -> { error = "Escribe un importe valido"; return }
            origen == null -> { error = "Elige la cuenta de origen"; return }
            destino == null -> { error = "Elige la cuenta de destino"; return }
            origen == destino -> {
                error = "El origen y el destino no pueden ser la misma cuenta"
                return
            }
        }
        error = null
        viewModelScope.launch {
            repo.guardaTransferencia(
                fecha = fecha,
                importeCentavos = centavos!!,
                cuentaOrigenId = origen!!,
                cuentaDestinoId = destino!!,
                descripcion = descripcion.ifBlank { "Transferencia entre cuentas" },
                nota = nota,
                grupoExistente = grupo,
                idsAReemplazar = idsSueltos,
                categoriaId = categoriaId
            )
            _cerrar.value = true
        }
    }

    /** Borrar una transferencia borra sus dos patas: media no es un estado valido. */
    fun elimina() {
        val id = movimientoId ?: return
        viewModelScope.launch {
            repo.movimiento(id)?.let { repo.eliminaMovimiento(it) }
            _cerrar.value = true
        }
    }
}
