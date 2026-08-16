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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
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
    contenedor: Contenedor,
    private val movimientoId: Long?
) : ViewModel() {

    private val repo = contenedor.repositorio

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
            origen == destino -> { error = "El origen y el destino no pueden ser la misma cuenta"; return }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferenciaPantalla(
    contenedor: Contenedor,
    movimientoId: Long? = null,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("transferencia-${movimientoId ?: 0}") {
        TransferenciaVm(contenedor, movimientoId)
    }
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val cerrar by vm.cerrar.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current
    var muestraCalendario by remember { mutableStateOf(false) }

    LaunchedEffect(cerrar) { if (cerrar) alCerrar() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (vm.esEdicion) "Editar transferencia" else "Transferencia") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                if (vm.esEdicion) {
                    IconButton(onClick = vm::elimina) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar transferencia",
                            tint = colores.salida
                        )
                    }
                }
            }
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                when {
                    vm.aMedias -> "Esta transferencia quedo a medias. Completa la cuenta que " +
                        "falta y al guardar se rehacen sus dos renglones."
                    vm.esEdicion -> "Se reescriben los dos renglones a la vez: la salida del " +
                        "origen y la entrada al destino."
                    else -> "Se generan los dos renglones automaticamente: la salida del origen " +
                        "y la entrada al destino, ligados entre si."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.aMedias) colores.alerta else colores.textoTenue
            )

            OutlinedTextField(
                value = vm.importeTexto,
                onValueChange = { vm.importeTexto = it },
                label = { Text("Importe") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vm.fecha.toString(),
                onValueChange = {},
                label = { Text("Fecha") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { muestraCalendario = true }) { Text("Cambiar") }
                }
            )

            SelectorDesplegable(
                etiqueta = "Sale de",
                valor = cuentas.firstOrNull { it.id == vm.origenId }?.nombre.orEmpty(),
                opciones = cuentas.map { it.id to it.nombre },
                alElegir = { vm.origenId = it }
            )

            SelectorDesplegable(
                etiqueta = "Entra a",
                valor = cuentas.firstOrNull { it.id == vm.destinoId }?.nombre.orEmpty(),
                opciones = cuentas.filter { it.id != vm.origenId }.map { it.id to it.nombre },
                alElegir = { vm.destinoId = it }
            )

            OutlinedTextField(
                value = vm.descripcion,
                onValueChange = { vm.descripcion = it },
                label = { Text("Descripcion") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let {
                Text(it, color = colores.salida, style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = vm::guarda, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.esEdicion) "Guardar cambios" else "Guardar transferencia")
            }
        }
    }

    if (muestraCalendario) {
        val estado = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = vm.fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { muestraCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        vm.fecha = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    muestraCalendario = false
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { muestraCalendario = false }) { Text("Cancelar") }
            }
        ) { DatePicker(state = estado) }
    }
}
