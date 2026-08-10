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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.db.Presupuesto
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.ui.components.BarraAvance
import mx.ollin.finanzas.ui.components.EstadoVacio
import mx.ollin.finanzas.ui.components.TarjetaCifra
import mx.ollin.finanzas.ui.components.TarjetaValor
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
import java.time.YearMonth
import kotlin.math.abs

data class RenglonMeta(
    val categoriaId: Long,
    val nombre: String,
    val metaCentavos: Long,
    val realCentavos: Long
) {
    val avance: Double get() = if (metaCentavos <= 0L) 0.0 else abs(realCentavos).toDouble() / metaCentavos
    val restanteCentavos: Long get() = metaCentavos - abs(realCentavos)
}

class PresupuestoVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    private val _mes = MutableStateFlow(YearMonth.now())
    val mes: StateFlow<YearMonth> = _mes

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metasDelMes = _mes.flatMapLatest { repo.observaPresupuestos(it.year, it.monthValue) }

    private val recalcula = MutableStateFlow(0)

    val renglones: StateFlow<List<RenglonMeta>> =
        combine(metasDelMes, repo.observaCategorias(), _mes, recalcula) { metas, categorias, mes, _ ->
            val porCategoria = metas.associateBy { it.categoriaId }
            categorias
                .filter { it.padreId != null && it.tipo == TipoCategoria.GASTO }
                .map { categoria ->
                    RenglonMeta(
                        categoriaId = categoria.id,
                        nombre = categoria.nombre,
                        metaCentavos = porCategoria[categoria.id]?.montoCentavos ?: 0L,
                        realCentavos = repo.totalCategoriaEnPeriodo(categoria.id, mes.toString())
                    )
                }
                // Primero lo que tiene meta, luego lo que gastaste sin tenerla.
                .sortedWith(compareByDescending<RenglonMeta> { it.metaCentavos > 0L }
                    .thenByDescending { abs(it.realCentavos) })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cambiaMes(delta: Long) {
        _mes.value = _mes.value.plusMonths(delta)
    }

    fun guardaMeta(categoriaId: Long, texto: String) {
        val centavos = Dinero.parsea(texto)?.let { abs(it) } ?: 0L
        val mes = _mes.value
        viewModelScope.launch {
            if (centavos == 0L) repo.eliminaPresupuesto(categoriaId, mes.year, mes.monthValue)
            else repo.guardaPresupuesto(
                Presupuesto(
                    categoriaId = categoriaId,
                    anio = mes.year,
                    mes = mes.monthValue,
                    montoCentavos = centavos
                )
            )
            recalcula.value++
        }
    }

    fun copiaDelMesAnterior(alTerminar: (Int) -> Unit) {
        val destino = _mes.value
        val origen = destino.minusMonths(1)
        viewModelScope.launch {
            val n = repo.copiaPresupuesto(origen.year, origen.monthValue, destino.year, destino.monthValue)
            recalcula.value++
            alTerminar(n)
        }
    }
}

@Composable
fun PresupuestoPantalla(contenedor: Contenedor, alAbrirCategorias: () -> Unit) {
    val vm = recuerdaVm("presupuesto") { PresupuestoVm(contenedor) }
    val mes by vm.mes.collectAsStateWithLifecycle()
    val renglones by vm.renglones.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<RenglonMeta?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val metaTotal = renglones.sumOf { it.metaCentavos }
    val realTotal = renglones.sumOf { abs(it.realCentavos) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.cambiaMes(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Mes anterior")
                }
                Text(mes.toString(), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { vm.cambiaMes(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mes siguiente")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaCifra("Presupuestado", metaTotal, Modifier.weight(1f))
                TarjetaCifra(
                    "Gastado",
                    -realTotal,
                    Modifier.weight(1f),
                    coloreado = true,
                    nota = if (metaTotal > 0) "%.0f%% de la meta".format(realTotal * 100.0 / metaTotal) else null
                )
            }
        }

        if (metaTotal > 0) {
            item {
                TarjetaValor(
                    etiqueta = if (metaTotal >= realTotal) "Te queda" else "Te pasaste por",
                    valor = Dinero.formatea(abs(metaTotal - realTotal)),
                    modifier = Modifier.fillMaxWidth(),
                    color = if (metaTotal >= realTotal) colores.entrada else colores.salida
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    vm.copiaDelMesAnterior { n ->
                        aviso = if (n == 0) "El mes anterior no tenia metas capturadas."
                        else "Se copiaron $n metas de ${mes.minusMonths(1)}."
                    }
                }) { Text("Copiar metas del mes anterior") }
                TextButton(onClick = alAbrirCategorias) { Text("Categorias") }
            }
        }

        if (renglones.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Filled.AccountBalanceWallet,
                    titulo = "Sin categorias de gasto",
                    detalle = "Crea las tuyas para empezar a presupuestar.",
                    modifier = Modifier.fillMaxWidth(),
                    accion = {
                        TextButton(onClick = alAbrirCategorias) { Text("Administrar categorias") }
                    }
                )
            }
        }

        items(renglones, key = { it.categoriaId }) { renglon ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { editando = renglon }
                    .padding(vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(renglon.nombre, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (renglon.metaCentavos > 0)
                            "${Dinero.formateaCorto(abs(renglon.realCentavos))} / ${Dinero.formateaCorto(renglon.metaCentavos)}"
                        else Dinero.formateaCorto(abs(renglon.realCentavos)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            renglon.metaCentavos == 0L -> colores.textoTenue
                            renglon.avance > 1.0 -> colores.salida
                            else -> colores.entrada
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (renglon.metaCentavos > 0) {
                    BarraAvance(renglon.avance, Modifier.fillMaxWidth())
                } else {
                    Text(
                        "Sin meta. Toca para ponerle una.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
        }
    }

    editando?.let { renglon ->
        var texto by remember(renglon.categoriaId) {
            mutableStateOf(
                if (renglon.metaCentavos > 0) Dinero.aTextoHoja(renglon.metaCentavos) else ""
            )
        }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text(renglon.nombre) },
            text = {
                Column {
                    Text(
                        "Llevas gastado ${Dinero.formatea(abs(renglon.realCentavos))} este mes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Meta mensual") },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text(
                        "Deja el campo vacio para quitar la meta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.guardaMeta(renglon.categoriaId, texto)
                    editando = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editando = null }) { Text("Cancelar") }
            }
        )
    }

    aviso?.let { mensaje ->
        AlertDialog(
            onDismissRequest = { aviso = null },
            text = { Text(mensaje) },
            confirmButton = { TextButton(onClick = { aviso = null }) { Text("Listo") } }
        )
    }
}
