package com.carlosalbertoxw.ollin.finanzas.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.SaldoCuenta
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.Medio
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCuenta
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

class CuentasVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    val saldos: StateFlow<List<SaldoCuenta>> = repo.observaSaldos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cuentas: StateFlow<List<Cuenta>> = repo.observaTodasLasCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(cuenta: Cuenta, alLograr: () -> Unit, alFallar: (String) -> Unit) {
        viewModelScope.launch {
            // El indice unico sobre el nombre rebota los duplicados. Se revisa
            // antes para poder decir cual estorba: si la que choca esta archivada,
            // el nombre se ve libre y el rechazo no se entiende solo.
            val choque = repo.cuentaPorNombre(cuenta.nombre)
            if (choque != null && choque.id != cuenta.id) {
                alFallar(
                    "Ya existe una cuenta llamada \"${choque.nombre}\"" +
                        (if (choque.archivada) ", archivada" else "") +
                        ". Usa otro nombre."
                )
                return@launch
            }
            runCatching { repo.guardaCuenta(cuenta) }
                .onSuccess { alLograr() }
                .onFailure { alFallar("No se pudo guardar la cuenta: revisa que el nombre no este repetido.") }
        }
    }

    fun elimina(cuenta: Cuenta) {
        // Room bloquea el borrado si la cuenta tiene movimientos (RESTRICT),
        // asi que en ese caso se archiva en vez de romper el historial.
        viewModelScope.launch {
            runCatching { repo.eliminaCuenta(cuenta) }
                .onFailure { repo.guardaCuenta(cuenta.copy(archivada = true)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentasPantalla(contenedor: Contenedor, alCerrar: () -> Unit) {
    val vm = recuerdaVm("cuentas") { CuentasVm(contenedor) }
    val saldos by vm.saldos.collectAsStateWithLifecycle()
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<Cuenta?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Cuentas") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                IconButton(onClick = {
                    editando = Cuenta(nombre = "", tipo = TipoCuenta.DEBITO, orden = cuentas.size)
                }) { Icon(Icons.Filled.Add, contentDescription = "Nueva cuenta") }
            }
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            TipoCuenta.entries.forEach { tipo ->
                val delTipo = saldos.filter { it.tipo == tipo }
                if (delTipo.isEmpty()) return@forEach

                item(key = "cab-${tipo.name}") {
                    SeccionTitulo(tipo.etiqueta, Modifier.padding(vertical = 10.dp)) {
                        TextoDinero(
                            delTipo.sumOf { it.saldoCentavos },
                            coloreado = tipo.esDeuda
                        )
                    }
                }

                items(delTipo, key = { it.cuentaId }) { saldo ->
                    val cuenta = cuentas.firstOrNull { it.id == saldo.cuentaId }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { cuenta?.let { editando = it } }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(saldo.nombre, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildString {
                                    append("${saldo.movimientos} movimientos")
                                    if (!saldo.incluirEnPatrimonio) append("  ·  fuera del patrimonio")
                                    if (cuenta?.archivada == true) append("  ·  archivada")
                                    saldo.limiteCentavos?.let {
                                        val uso = kotlin.math.abs(saldo.saldoCentavos) * 100.0 / it
                                        append("  ·  %.0f%% del limite".format(uso))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.textoTenue
                            )
                        }
                        TextoDinero(saldo.saldoCentavos, coloreado = saldo.saldoCentavos < 0)
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Las cuentas de tipo Activo (terreno, cripto, bienes) hacen que comprar " +
                        "patrimonio deje de verse como gasto: el dinero sale de una cuenta y " +
                        "entra a otra, en vez de desaparecer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }
        }
    }

    editando?.let { cuenta ->
        DialogoCuenta(
            cuenta = cuenta,
            // El dialogo se cierra hasta que el guardado pasa: si el nombre choca,
            // se queda abierto con todo lo capturado para corregir solo eso.
            alGuardar = { propuesta ->
                vm.guarda(
                    cuenta = propuesta,
                    alLograr = { editando = null },
                    alFallar = { aviso = it }
                )
            },
            alEliminar = { vm.elimina(cuenta); editando = null },
            alCancelar = { editando = null }
        )
    }

    aviso?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { aviso = null },
            title = { Text("No se pudo guardar") },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = { aviso = null }) { Text("Entendido") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoCuenta(
    cuenta: Cuenta,
    alGuardar: (Cuenta) -> Unit,
    alEliminar: () -> Unit,
    alCancelar: () -> Unit
) {
    var nombre by remember(cuenta.id) { mutableStateOf(cuenta.nombre) }
    var tipo by remember(cuenta.id) { mutableStateOf(cuenta.tipo) }
    var limite by remember(cuenta.id) {
        mutableStateOf(cuenta.limiteCentavos?.let { Dinero.aTextoHoja(it) } ?: "")
    }
    var enPatrimonio by remember(cuenta.id) { mutableStateOf(cuenta.incluirEnPatrimonio) }
    var soloElectronico by remember(cuenta.id) { mutableStateOf(cuenta.soloElectronico) }
    var medio by remember(cuenta.id) { mutableStateOf(cuenta.medioPorDefecto) }
    var abierto by remember { mutableStateOf(false) }

    // El efectivo no admite discusion, y una cuenta solo electronica tampoco:
    // en esos dos casos el medio lo dicta la cuenta y no se ofrece elegirlo.
    val medioEfectivo = tipo == TipoCuenta.EFECTIVO
    val medioFinal = when {
        medioEfectivo -> Medio.EFECTIVO
        soloElectronico -> Medio.ELECTRONICO
        else -> medio
    }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (cuenta.id == 0L) "Nueva cuenta" else "Editar cuenta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
                    OutlinedTextField(
                        value = tipo.etiqueta,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Naturaleza") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                        TipoCuenta.entries.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion.etiqueta) },
                                onClick = {
                                    tipo = opcion
                                    // Al cambiar de naturaleza se propone el valor
                                    // habitual; una tarjeta nace solo electronica
                                    // y un prestamo no. Se puede corregir abajo.
                                    soloElectronico = opcion == TipoCuenta.CREDITO ||
                                        opcion == TipoCuenta.CREDITO_MSI
                                    abierto = false
                                }
                            )
                        }
                    }
                }

                if (tipo.esDeuda) {
                    OutlinedTextField(
                        value = limite,
                        onValueChange = { limite = it },
                        label = { Text("Limite de credito (opcional)") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                if (!medioEfectivo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Solo electronica", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Enciendelo para una tarjeta, por la que nunca pasa dinero en " +
                                    "mano. Apagalo para un prestamo o una cuenta por la que si " +
                                    "recibes efectivo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalColoresOllin.current.textoTenue
                            )
                        }
                        Switch(checked = soloElectronico, onCheckedChange = { soloElectronico = it })
                    }

                    if (!soloElectronico) {
                        Text("Medio habitual", style = MaterialTheme.typography.bodyMedium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Medio.entries.forEachIndexed { i, opcion ->
                                SegmentedButton(
                                    selected = medio == opcion,
                                    onClick = { medio = opcion },
                                    shape = SegmentedButtonDefaults.itemShape(i, Medio.entries.size)
                                ) { Text(opcion.etiqueta) }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cuenta a mi nombre", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Apagalo para el dinero que administras pero no es tuyo. Sigue " +
                                "registrando sus movimientos, pero deja de sumar a tu " +
                                "patrimonio, tu liquidez y tu fondo de emergencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalColoresOllin.current.textoTenue
                        )
                    }
                    Switch(
                        checked = enPatrimonio,
                        onCheckedChange = { enPatrimonio = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        cuenta.copy(
                            nombre = nombre.trim(),
                            tipo = tipo,
                            medioPorDefecto = medioFinal,
                            limiteCentavos = Dinero.parsea(limite)?.let { kotlin.math.abs(it) },
                            incluirEnPatrimonio = enPatrimonio,
                            soloElectronico = !medioEfectivo && soloElectronico
                        )
                    )
                },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (cuenta.id != 0L) {
                    TextButton(onClick = alEliminar) { Text("Eliminar") }
                }
                TextButton(onClick = alCancelar) { Text("Cancelar") }
            }
        }
    )
}
