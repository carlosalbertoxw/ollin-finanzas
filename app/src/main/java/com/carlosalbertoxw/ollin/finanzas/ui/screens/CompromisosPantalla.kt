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
                    compareBy<Compromiso> { proximoPago(it) }.thenBy { it.nombre.lowercase() }
                ),
                // Al reves que los activos: en lo cerrado lo que se busca es lo
                // ultimo que se cerro, no lo mas viejo del archivo.
                archivados = archivados.sortedWith(
                    compareByDescending<Compromiso> { proximoPago(it) }
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

private fun proximoPago(c: Compromiso): LocalDate = c.proximoPago

private fun pendiente(c: Compromiso): Long = c.montoCentavos * (c.pagosRestantes ?: 0)

/**
 * Como termino un compromiso archivado: llego a su ultimo pago o se cerro sin
 * pagarlo. Sin totalPagos el plan era indefinido y solo un unico pudo haberlo
 * archivado, asi que uno es el minimo con el que se compara.
 */
private fun cierre(c: Compromiso): String =
    if (c.pagosRealizados >= (c.totalPagos ?: 1)) "Cumplido" else "Descartado"

/**
 * La tarjeta de un compromiso. La misma en la lista de pendientes y en el
 * archivo: lo que cambia es que solo los activos piden algo, y por eso solo
 * ellos muestran el renglon de la fecha y el boton de registrar.
 */
@Composable
private fun TarjetaCompromiso(
    c: Compromiso,
    categorias: List<Categoria>,
    alEditar: () -> Unit,
    alPagar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    val fecha = proximoPago(c)
    val vencido = c.activo && fecha.isBefore(LocalDate.now())

    Card(
        modifier.fillMaxWidth().clickable(onClick = alEditar),
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
                            // En el archivo, saber que paso con el ultimo pago
                            // es justo lo que uno viene a consultar.
                            if (!c.activo) append("  ·  ${cierre(c)}")
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
                    TextButton(onClick = alPagar) { Text("Registrar") }
                }
            }
        }
    }
}

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
    // Plegados de entrada: la pantalla es para ver que se debe, no que se pago.
    var abreArchivados by remember { mutableStateOf(false) }

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
                // El titulo es la señal de que el boton cambio de lista, no de
                // que abrio una seccion mas: son dos vistas, no una sola
                // revuelta.
                title = { Text(if (abreArchivados) "Archivados" else "Compromisos") },
                navigationIcon = {
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Cambia de lista entera en vez de desplegar una seccion
                    // al final: lo cerrado y lo pendiente se consultan en
                    // momentos distintos y mezclados no se leen. Vive en la
                    // barra para poder entrar al archivo tambien cuando esta
                    // vacio; colgado de la lista solo aparecia si ya habia algo
                    // dentro, y entonces nadie se enteraba de que existe.
                    IconToggleButton(
                        checked = abreArchivados,
                        onCheckedChange = { abreArchivados = it }
                    ) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = if (abreArchivados) "Ver pendientes"
                            else "Ver archivados",
                            tint = if (abreArchivados) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        // Lo que se da de alta nace pendiente: si el alta ocurre
                        // con el archivo en pantalla, se vuelve a la lista donde
                        // el compromiso nuevo si se va a ver.
                        abreArchivados = false
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

            if (abreArchivados) {
                // Solo lo cerrado. Nada de cifras ni de deslizamientos: aqui ya
                // no hay ninguna decision que tomar, solo historial que mirar.
                if (compromisos.archivados.isEmpty()) {
                    EstadoVacio(
                        icono = Icons.Filled.Inventory2,
                        titulo = "Nada archivado todavia",
                        detalle = "Aqui van a caer los pagos unicos que cumplas o descartes " +
                            "y los planes a plazos que lleguen a su ultima mensualidad.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(compromisos.archivados, key = { it.id }) { c ->
                            TarjetaCompromiso(
                                c = c,
                                categorias = categorias,
                                alEditar = { editando = c },
                                alPagar = { alPagar(c.id) },
                                modifier = Modifier.animateItem()
                            )
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Un compromiso archivado ya no suma en las cifras ni entra en " +
                                    "el recordatorio diario. Sigue siendo tuyo: se abre para " +
                                    "consultarlo o borrarlo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.textoTenue
                            )
                        }
                    }
                }
            } else if (compromisos.vacia) {
                EstadoVacio(
                    icono = Icons.Filled.EventRepeat,
                    titulo = "Sin compromisos",
                    detalle = "Registra tus meses sin intereses, suscripciones y gastos anuales " +
                        "para que dejen de llegar de sorpresa.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val totalPendiente = compromisos.activos.sumOf { pendiente(it) }
                // Todo lo que se repite al menos una vez al mes, llevado a lo
                // que pesa en un mes: dejar fuera lo semanal y lo quincenal
                // haria que la cifra subestimara justo la carga mas seguida.
                val mensualFijo = compromisos.activos
                    .filter { it.periodicidad.cabeEnUnMes }
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

                    items(compromisos.activos, key = { it.id }) { c ->
                        FilaDeslizable(
                            habilitada = true,
                            // Cumplir cambia la fecha y con ella el lugar en la
                            // lista: animado se ve a donde se fue la tarjeta.
                            // Cuando el cumplimiento lo archiva, se ve irse.
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
                            TarjetaCompromiso(
                                c = c,
                                categorias = categorias,
                                alEditar = { editando = c },
                                alPagar = { alPagar(c.id) }
                            )
                        }
                    }

                    // Que no quede nada pendiente es una noticia, no un hueco.
                    if (compromisos.activos.isEmpty()) {
                        item {
                            Text(
                                if (compromisos.archivados.size == 1)
                                    "No queda ningun compromiso pendiente. El que ya cerraste " +
                                        "esta en el archivo, con el boton de la caja."
                                else
                                    "No queda ningun compromiso pendiente. Los " +
                                        "${compromisos.archivados.size} que ya cerraste estan " +
                                        "en el archivo, con el boton de la caja.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colores.textoTenue,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Desliza un compromiso a la derecha para darlo por cumplido o descartar " +
                                "ese pago. Nada avanza solo: hasta que decidas, sigue pendiente y " +
                                "Ollin Finanzas te lo recuerda una vez al dia. Lo que ya no pide " +
                                "nada -- un pago unico resuelto o un plan que llego a su ultima " +
                                "mensualidad -- se archiva y deja de avisar; el boton de la caja, " +
                                "arriba, cambia a esa lista.",
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
                    label = { Text(if (periodicidad.esUnico) "Fecha del pago" else "Siguiente pago") },
                    readOnly = true,
                    supportingText = {
                        // El dia del mes solo significa algo si el paso son
                        // meses: un plan semanal cae en un dia distinto cada vez,
                        // y uno unico no cae una segunda vez en ningun dia.
                        Text(
                            if (periodicidad.esUnico || periodicidad.dias > 0) periodicidad.cada
                            else "${periodicidad.cada} el dia ${siguientePago.dayOfMonth}"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { muestraCalendario = true }) { Text("Cambiar") }
                    }
                )
                // Un compromiso unico no admite plazos: es exactamente un pago,
                // y preguntarlo solo abriria la puerta a contradecir la cadencia.
                if (!periodicidad.esUnico) {
                    // Va al final a proposito: un desplegable como ultimo campo abre
                    // su menu encima de los botones del dialogo.
                    OutlinedTextField(
                        value = totalPagos,
                        onValueChange = { totalPagos = it.filter(Char::isDigit) },
                        label = { Text("Numero de pagos (vacio = indefinido)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                } else {
                    Text(
                        "Se paga una sola vez. Al cumplirlo o descartarlo pasa a Archivados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalColoresOllin.current.textoTenue
                    )
                }
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
                            // Un unico es un plan de un solo pago, y es ese
                            // contador -- no un caso aparte -- lo que lo archiva
                            // al cumplirlo. Se cuenta sobre los pagos ya hechos
                            // para que convertir un plan viejo en unico deje
                            // exactamente uno por delante y no borre su historia.
                            totalPagos = if (periodicidad.esUnico) compromiso.pagosRealizados + 1
                            else totalPagos.toIntOrNull(),
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
