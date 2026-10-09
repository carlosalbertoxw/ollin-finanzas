package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Compromiso
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.Movimiento
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Contraparte
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaPantalla(
    repo: FinanzasRepositorio,
    ajustes: AjustesRepositorio,
    movimientoId: Long?,
    alCerrar: () -> Unit,
    alCambiarATransferencia: () -> Unit,
    alEditarTransferencia: (Long) -> Unit,
    compromisoId: Long? = null
) {
    val vm = recuerdaVm("captura-${movimientoId ?: 0}-${compromisoId ?: 0}") {
        CapturaVm(repo, ajustes, movimientoId, compromisoId)
    }
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    val muestraSaldoInicial by vm.muestraSaldoInicial.collectAsStateWithLifecycle()
    val cerrar by vm.cerrar.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    LaunchedEffect(cerrar) { if (cerrar) alCerrar() }

    var muestraCalendario by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    when {
                        vm.compromisoPagado != null -> "Pagar compromiso"
                        movimientoId == null -> "Nuevo movimiento"
                        else -> "Editar movimiento"
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                if (movimientoId != null) {
                    IconButton(onClick = vm::elimina) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint = colores.salida
                        )
                    }
                }
            }
        )

        // Una pata suelta no se edita aqui: guardar solo la mitad descuadraria el
        // patrimonio, asi que la unica salida es abrir la transferencia entera.
        if (vm.cargado && vm.esTraspaso && movimientoId != null) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Esto es media transferencia",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Una transferencia son dos renglones que se cancelan entre si, y aqui " +
                            "solo se ve uno. Se edita completa para que el par siga cuadrando.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { alEditarTransferencia(movimientoId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Editar la transferencia completa")
                    }
                }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val cuentasActivo = cuentas.filter { it.tipo == TipoCuenta.ACTIVO }

            // Al abrir una compra de patrimonio vieja (una salida suelta venida del
            // Excel) el destino no existe todavia: se sugiere para poder repararla.
            LaunchedEffect(vm.naturaleza, cuentasActivo) {
                if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO && vm.cuentaActivoId == null) {
                    vm.cuentaActivoId = cuentasActivo.firstOrNull()?.id
                }
            }

            // Chips y no segmentos: cuatro etiquetas no caben repartidas a lo ancho
            // de un telefono angosto, y aqui cada una se lee entera aunque haya
            // que deslizar.
            // Se puede esconder el saldo inicial desde Ajustes, pero nunca cuando
            // es lo que se esta editando: dejaria el movimiento sin forma de abrirse.
            val naturalezas = NaturalezaCaptura.entries.filter {
                it != NaturalezaCaptura.SALDO_INICIAL ||
                    muestraSaldoInicial ||
                    vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                naturalezas.forEach { opcion ->
                    FilterChip(
                        selected = vm.naturaleza == opcion,
                        onClick = { vm.alElegirNaturaleza(opcion, cuentasActivo) },
                        label = { Text(opcion.etiqueta) }
                    )
                }
            }

            if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO) {
                Text(
                    "Comprar un terreno o cripto no es gastar: el dinero sale de una cuenta y " +
                        "entra a otra. Se guarda como transferencia para que tu patrimonio no baje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            if (vm.naturaleza == NaturalezaCaptura.AJUSTE) {
                Text(
                    "Para revaluar o depreciar algo que ya tienes. No mueve dinero de ninguna " +
                        "cuenta ni cuenta como ingreso: solo cambia lo que vale el bien.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            OutlinedTextField(
                value = vm.importeTexto,
                onValueChange = { vm.importeTexto = it },
                label = {
                    Text(
                        if (vm.naturaleza == NaturalezaCaptura.AJUSTE) "Cuanto vale ahora"
                        else "Importe"
                    )
                },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Se escribe el valor de hoy y la app deduce el movimiento: nadie
            // sabe de memoria cuanto subio su terreno, pero si cuanto vale.
            val esAjusteConSaldo = vm.naturaleza == NaturalezaCaptura.AJUSTE &&
                vm.cuentaId != null &&
                saldos.isNotEmpty()
            if (esAjusteConSaldo) {
                val diferencia = vm.diferenciaDelAjuste()
                Text(
                    buildString {
                        append("Hoy esta en ${Dinero.formatea(vm.valorBase())}.")
                        if (diferencia != null && diferencia != 0L) {
                            append(if (diferencia > 0) "  Sube " else "  Baja ")
                            append(Dinero.formatea(kotlin.math.abs(diferencia)))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        diferencia == null || diferencia == 0L -> colores.textoTenue
                        diferencia > 0 -> colores.entrada
                        else -> colores.salida
                    }
                )
            }

            // Una tarjeta puede arrancar debiendo: el saldo inicial lleva su propio signo.
            if (vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !vm.saldoEnContra,
                        onClick = { vm.saldoEnContra = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("A favor") }
                    SegmentedButton(
                        selected = vm.saldoEnContra,
                        onClick = { vm.saldoEnContra = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("En contra") }
                }
            }

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

            // Revaluar una cuenta de banco no tiene sentido: si el saldo no cuadra
            // es que falta un movimiento, no que el dinero cambio de valor.
            val revaluables = cuentas.filter {
                it.tipo == TipoCuenta.ACTIVO || it.tipo == TipoCuenta.INVERSION
            }
            val elegibles = if (vm.naturaleza == NaturalezaCaptura.AJUSTE) revaluables else cuentas

            SelectorDesplegable(
                etiqueta = when (vm.naturaleza) {
                    NaturalezaCaptura.PATRIMONIO -> "Sale de"
                    NaturalezaCaptura.AJUSTE -> "Que bien"
                    else -> "Cuenta"
                },
                valor = elegibles.firstOrNull { it.id == vm.cuentaId }?.nombre.orEmpty(),
                opciones = elegibles.map { it.id to it.nombre },
                alElegir = { id -> elegibles.firstOrNull { it.id == id }?.let(vm::alElegirCuenta) }
            )

            if (vm.naturaleza == NaturalezaCaptura.AJUSTE && revaluables.isEmpty()) {
                Text(
                    "No tienes cuentas de tipo Activo ni Inversion. Crea una en Ajustes > " +
                        "Cuentas para poder registrar lo que vale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.alerta
                )
            }

            if (vm.naturaleza == NaturalezaCaptura.PATRIMONIO) {
                if (cuentasActivo.isEmpty()) {
                    Text(
                        "No tienes ninguna cuenta de tipo Activo. Crea una en Ajustes > Cuentas " +
                            "(por ejemplo \"Patrimonio\") para poder registrar la compra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.alerta
                    )
                } else {
                    SelectorDesplegable(
                        etiqueta = "Entra a",
                        valor = cuentasActivo
                            .firstOrNull { it.id == vm.cuentaActivoId }
                            ?.nombre.orEmpty(),
                        opciones = cuentasActivo.map { it.id to it.nombre },
                        alElegir = { vm.cuentaActivoId = it }
                    )
                }
            }

            if (vm.naturaleza != NaturalezaCaptura.SALDO_INICIAL &&
                vm.naturaleza != NaturalezaCaptura.AJUSTE
            ) {
                val aplicables = vm.categoriasAplicables(categorias)
                SelectorDesplegable(
                    etiqueta = "Categoria",
                    valor = aplicables.firstOrNull { it.id == vm.categoriaId }?.nombre.orEmpty(),
                    opciones = aplicables.map { it.id to it.nombre },
                    alElegir = { vm.categoriaId = it }
                )
            }

            OutlinedTextField(
                value = vm.descripcion,
                onValueChange = { vm.descripcion = it },
                label = { Text("Descripcion") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // En patrimonio y saldo inicial el medio lo dicta la cuenta, no el usuario.
            val eligeMedio = vm.naturaleza == NaturalezaCaptura.GASTO ||
                vm.naturaleza == NaturalezaCaptura.INGRESO
            if (eligeMedio) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Medio.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = vm.medio == m,
                            onClick = { vm.medio = m },
                            shape = SegmentedButtonDefaults.itemShape(i, Medio.entries.size)
                        ) { Text(m.etiqueta) }
                    }
                }
            }

            OutlinedTextField(
                value = vm.nota,
                onValueChange = { vm.nota = it },
                label = { Text("Nota (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Dos saldos iniciales en la misma cuenta se suman en silencio y el
            // saldo queda al doble sin que nada lo delate.
            vm.saldoInicialPrevio
                ?.takeIf { vm.naturaleza == NaturalezaCaptura.SALDO_INICIAL }
                ?.let { previo ->
                    Text(
                        "Esta cuenta ya tiene un saldo inicial de " +
                            "${Dinero.formatea(previo.importeCentavos)} " +
                            "con fecha ${previo.fecha}. Si guardas otro, los dos se suman: " +
                            "conviene editar el que ya existe.",
                        color = colores.alerta,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            vm.error?.let {
                Text(it, color = colores.salida, style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = vm::guarda, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar")
            }

            if (movimientoId == null) {
                TextButton(onClick = alCambiarATransferencia, modifier = Modifier.fillMaxWidth()) {
                    Text("Es un traspaso entre mis cuentas")
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (muestraCalendario) {
        val estado = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis =
            vm.fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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

/**
 * Desplegable simple sobre una lista de pares id/etiqueta.
 *
 * El id admite nulo porque hay listas donde "ninguno" es una opcion legitima y
 * no una cuenta disfrazada: el filtro de Movimientos ofrece "Todas las cuentas"
 * y necesita devolver el mismo nulo que entiende el repositorio. Un id centinela
 * como 0 habria funcionado hasta el dia en que alguna tabla empezara en 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorDesplegable(
    etiqueta: String,
    valor: String,
    opciones: List<Pair<Long?, String>>,
    alElegir: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = valor,
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (id, texto) ->
                DropdownMenuItem(
                    text = { Text(texto) },
                    onClick = { alElegir(id); abierto = false }
                )
            }
        }
    }
}
