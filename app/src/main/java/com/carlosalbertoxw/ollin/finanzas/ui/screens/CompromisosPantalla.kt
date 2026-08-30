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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CompromisosVm(private val repo: FinanzasRepositorio) : ViewModel() {


    /**
     * Lo urgente arriba. El orden lo manda el proximo pago, que no es una
     * columna sino la fecha del primero corrida por los pagos ya hechos, asi
     * que se ordena aqui: en SQL, cumplir uno lo dejaria en su lugar viejo.
     */
    val compromisos: StateFlow<List<Compromiso>> = repo.observaCompromisos()
        .map { lista ->
            lista.sortedWith(
                compareByDescending<Compromiso> { it.activo }
                    .thenBy { proximoPago(it) }
                    .thenBy { it.nombre.lowercase() }
            )
        }
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

private fun proximoPago(c: Compromiso): LocalDate = c.proximoPago

private fun pendiente(c: Compromiso): Long = c.montoCentavos * (c.pagosRestantes ?: 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompromisosPantalla(
    repo: FinanzasRepositorio,
    alPagar: (Long) -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("compromisos") { CompromisosVm(repo) }
    val compromisos by vm.compromisos.collectAsStateWithLifecycle()
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<Compromiso?>(null) }

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
            } else {
                val totalPendiente = compromisos.filter { it.activo }.sumOf { pendiente(it) }
                // Todo lo que se repite al menos una vez al mes, llevado a lo
                // que pesa en un mes: dejar fuera lo semanal y lo quincenal
                // haria que la cifra subestimara justo la carga mas seguida.
                val mensualFijo = compromisos
                    .filter { it.activo && it.periodicidad.cabeEnUnMes }
                    .sumOf { it.periodicidad.equivalenteMensual(it.montoCentavos) }

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
                        val fecha = proximoPago(c)
                        val vencido = c.activo && fecha.isBefore(LocalDate.now())

                        FilaDeslizable(
                            habilitada = c.activo,
                            // Cumplir cambia la fecha y con ella el lugar en la
                            // lista: animado se ve a donde se fue la tarjeta.
                            modifier = Modifier.animateItem(),
                            alCumplir = {
                                vm.cumple(c.id)
                                avisa("${c.nombre}: pago cumplido") { vm.deshaceCumplimiento(c.id) }
                            },
                            alDescartar = {
                                vm.descarta(c.id)
                                avisa("${c.nombre}: pago descartado") { vm.deshaceDescarte(c.id) }
                            }
                        ) {
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
                                            // Un pago atrasado no se cae de la lista: se queda
                                            // marcado hasta que se cumpla o se descarte.
                                            Text(
                                                (if (vencido) "Vencio el $fecha" else "Proximo: $fecha") +
                                                    if (pendiente(c) > 0) "  ·  faltan ${Dinero.formateaCorto(pendiente(c))}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (vencido) colores.alerta else colores.textoTenue,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { alPagar(c.id) }) { Text("Registrar") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Desliza un compromiso a la derecha para darlo por cumplido o descartar " +
                                "ese pago. Nada avanza solo: hasta que decidas, sigue pendiente y " +
                                "Ollin Finanzas te lo recuerda una vez al dia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                }
            }
        }

        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter))
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
                        // El dia del mes solo significa algo si el paso son
                        // meses: un plan semanal cae en un dia distinto cada vez.
                        Text(
                            if (periodicidad.dias > 0) periodicidad.cada
                            else "${periodicidad.cada} el dia ${siguientePago.dayOfMonth}"
                        )
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
                            // adelante, asi que se retrocede lo ya pagado y lo
                            // descartado. En un compromiso nuevo no hay nada que
                            // retroceder.
                            //
                            // Limitacion conocida de las cadencias en meses: si
                            // el dia elegido no existe en el mes del ancla (un 31
                            // retrocedido a febrero), retroceder lo recorta y el
                            // proximo pago cae unos dias antes del elegido. No hay
                            // ancla que lo evite: ninguna fecha de febrero mas un
                            // mes da un 31 de marzo. Resolverlo pide guardar el dia
                            // de pago aparte del ancla. Las cadencias en dias no
                            // tienen el problema: restar dias es exacto.
                            fechaPrimerPago = periodicidad.retrocede(
                                siguientePago,
                                (compromiso.pagosRealizados + compromiso.pagosDescartados).toLong()
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
