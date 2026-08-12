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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.Compromiso
import mx.ollin.finanzas.data.db.Cuenta
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.Periodicidad
import mx.ollin.finanzas.ui.components.EstadoVacio
import mx.ollin.finanzas.ui.components.TarjetaCifra
import mx.ollin.finanzas.ui.components.TextoDinero
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CompromisosVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    val compromisos: StateFlow<List<Compromiso>> = repo.observaCompromisos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    // El pago no se registra aqui: "Pagar" abre una captura precargada y el
    // contador lo avanza el repositorio cuando el movimiento queda guardado.
}

private fun proximoPago(c: Compromiso): LocalDate =
    c.fechaPrimerPago.plusMonths(c.pagosRealizados.toLong() * c.periodicidad.meses)

private fun pendiente(c: Compromiso): Long {
    val restantes = c.totalPagos?.let { (it - c.pagosRealizados).coerceAtLeast(0) } ?: return 0L
    return c.montoCentavos * restantes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompromisosPantalla(
    contenedor: Contenedor,
    alPagar: (Long) -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("compromisos") { CompromisosVm(contenedor) }
    val compromisos by vm.compromisos.collectAsStateWithLifecycle()
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<Compromiso?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Compromisos") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                IconButton(onClick = {
                    editando = Compromiso(
                        nombre = "",
                        cuentaId = cuentas.firstOrNull()?.id,
                        categoriaId = null,
                        montoCentavos = 0L,
                        fechaPrimerPago = LocalDate.now()
                    )
                }) { Icon(Icons.Filled.Add, contentDescription = "Nuevo compromiso") }
            }
        )

        if (compromisos.isEmpty()) {
            EstadoVacio(
                icono = Icons.Filled.EventRepeat,
                titulo = "Sin compromisos",
                detalle = "Registra tus meses sin intereses, suscripciones y gastos anuales " +
                    "para que dejen de llegar de sorpresa.",
                modifier = Modifier.fillMaxSize()
            )
            editando?.let { c ->
                DialogoCompromiso(
                    compromiso = c,
                    cuentas = cuentas,
                    categorias = categorias,
                    alGuardar = { vm.guarda(it); editando = null },
                    alCancelar = { editando = null },
                    alEliminar = null
                )
            }
            return
        }

        val totalPendiente = compromisos.filter { it.activo }.sumOf { pendiente(it) }
        val mensualFijo = compromisos
            .filter { it.activo && it.periodicidad == Periodicidad.MENSUAL }
            .sumOf { it.montoCentavos }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaCifra(
                        "Comprometido a futuro", -totalPendiente, Modifier.weight(1f),
                        nota = "solo planes con fin"
                    )
                    TarjetaCifra(
                        "Carga fija mensual", -mensualFijo, Modifier.weight(1f),
                        nota = "suscripciones y MSI"
                    )
                }
            }

            items(compromisos, key = { it.id }) { c ->
                Card(
                    Modifier.fillMaxWidth().clickable { editando = c },
                    colors = CardDefaults.cardColors(
                        containerColor = if (c.activo) MaterialTheme.colorScheme.surfaceContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.nombre, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildString {
                                        append(c.periodicidad.etiqueta)
                                        categorias.firstOrNull { it.id == c.categoriaId }?.let {
                                            append("  ·  ${it.nombre}")
                                        }
                                        c.totalPagos?.let {
                                            append("  ·  ${c.pagosRealizados}/$it pagos")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colores.textoTenue
                                )
                            }
                            TextoDinero(-c.montoCentavos)
                        }

                        if (c.activo) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Proximo: ${proximoPago(c)}" +
                                        if (pendiente(c) > 0) "  ·  faltan ${Dinero.formateaCorto(pendiente(c))}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colores.textoTenue
                                )
                                TextButton(onClick = { alPagar(c.id) }) { Text("Pagar") }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Ollin Finanzas revisa una vez al dia y te avisa cuando un compromiso esta por vencer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }
        }
    }

    editando?.let { c ->
        DialogoCompromiso(
            compromiso = c,
            cuentas = cuentas,
            categorias = categorias,
            alGuardar = { vm.guarda(it); editando = null },
            alCancelar = { editando = null },
            alEliminar = if (c.id != 0L) ({ vm.elimina(c); editando = null }) else null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoCompromiso(
    compromiso: Compromiso,
    cuentas: List<Cuenta>,
    categorias: List<Categoria>,
    alGuardar: (Compromiso) -> Unit,
    alCancelar: () -> Unit,
    alEliminar: (() -> Unit)?
) {
    var nombre by remember(compromiso.id) { mutableStateOf(compromiso.nombre) }
    var monto by remember(compromiso.id) {
        mutableStateOf(if (compromiso.montoCentavos > 0) Dinero.aTextoHoja(compromiso.montoCentavos) else "")
    }
    var periodicidad by remember(compromiso.id) { mutableStateOf(compromiso.periodicidad) }
    var totalPagos by remember(compromiso.id) { mutableStateOf(compromiso.totalPagos?.toString() ?: "") }
    var cuentaId by remember(compromiso.id) { mutableStateOf(compromiso.cuentaId) }
    var categoriaId by remember(compromiso.id) { mutableStateOf(compromiso.categoriaId) }
    // Se pide el siguiente pago y no el primero: para una suscripcion que lleva
    // años corriendo, la fecha que el usuario tiene en la cabeza es la que sigue.
    var siguientePago by remember(compromiso.id) { mutableStateOf(proximoPago(compromiso)) }
    var muestraCalendario by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (compromiso.id == 0L) "Nuevo compromiso" else compromiso.nombre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it },
                    label = { Text("Monto de cada pago") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                SelectorDesplegable(
                    etiqueta = "Cuenta que lo paga",
                    valor = cuentas.firstOrNull { it.id == cuentaId }?.nombre.orEmpty(),
                    opciones = cuentas.map { it.id to it.nombre },
                    alElegir = { cuentaId = it }
                )
                // Es lo que permite que "Pagar" abra la captura ya clasificada,
                // y de paso lo que ata el compromiso a su rubro del presupuesto.
                SelectorDesplegable(
                    etiqueta = "Categoria",
                    valor = categorias.firstOrNull { it.id == categoriaId }?.nombre.orEmpty(),
                    opciones = categorias.map { it.id to it.nombre },
                    alElegir = { categoriaId = it }
                )
                MenuEnum(
                    etiqueta = "Periodicidad",
                    valor = periodicidad.etiqueta,
                    opciones = Periodicidad.entries.map { it.etiqueta },
                    alElegir = { i -> periodicidad = Periodicidad.entries[i] }
                )
                OutlinedTextField(
                    value = siguientePago.toString(),
                    onValueChange = {},
                    label = { Text("Siguiente pago") },
                    readOnly = true,
                    supportingText = {
                        val cada = if (periodicidad.meses == 1) "Cada mes"
                        else "Cada ${periodicidad.meses} meses"
                        Text("$cada el dia ${siguientePago.dayOfMonth}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { muestraCalendario = true }) { Text("Cambiar") }
                    }
                )
                // Va al final a proposito: un desplegable como ultimo campo abre
                // su menu encima de los botones del dialogo.
                OutlinedTextField(
                    value = totalPagos,
                    onValueChange = { totalPagos = it.filter(Char::isDigit) },
                    label = { Text("Numero de pagos (vacio = indefinido)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        compromiso.copy(
                            nombre = nombre.trim(),
                            montoCentavos = Dinero.parsea(monto)?.let { kotlin.math.abs(it) } ?: 0L,
                            periodicidad = periodicidad,
                            // La entidad ancla en el primer pago y cuenta hacia
                            // adelante, asi que se retrocede lo ya pagado. En un
                            // compromiso nuevo no hay nada que retroceder.
                            fechaPrimerPago = siguientePago.minusMonths(
                                compromiso.pagosRealizados.toLong() * periodicidad.meses
                            ),
                            totalPagos = totalPagos.toIntOrNull(),
                            cuentaId = cuentaId,
                            categoriaId = categoriaId
                        )
                    )
                },
                enabled = nombre.isNotBlank() && Dinero.parsea(monto) != null
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                alEliminar?.let { TextButton(onClick = it) { Text("Eliminar") } }
                TextButton(onClick = alCancelar) { Text("Cancelar") }
            }
        }
    )

    if (muestraCalendario) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = siguientePago.atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { muestraCalendario = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let {
                        siguientePago = Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC).toLocalDate()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuEnum(
    etiqueta: String,
    valor: String,
    opciones: List<String>,
    alElegir: (Int) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEachIndexed { i, texto ->
                DropdownMenuItem(text = { Text(texto) }, onClick = { alElegir(i); abierto = false })
            }
        }
    }
}
