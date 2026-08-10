package mx.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.MovimientoDetallado
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.usecase.Hallazgo
import mx.ollin.finanzas.ui.components.EstadoVacio
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

/**
 * La lista concreta detras de un hallazgo de Salud de los datos.
 *
 * El aviso solo dice cuantos estan mal; aqui se ven cuales y se arreglan, ya
 * sea abriendo el movimiento o, cuando lo unico que falta es la categoria,
 * eligiendola sin salir de la lista.
 */
class RevisionVm(
    private val contenedor: Contenedor,
    private val clave: String
) : ViewModel() {

    private val repo = contenedor.repositorio

    private val _hallazgo = MutableStateFlow<Hallazgo?>(null)
    val hallazgo: StateFlow<Hallazgo?> = _hallazgo

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val movimientos: StateFlow<List<MovimientoDetallado>> = _hallazgo
        .flatMapLatest { repo.observaMovimientosPorIds(it?.idsMovimiento.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sin apagar la lista: la revision se repite al volver de editar un movimiento. */
    fun revisa() {
        viewModelScope.launch {
            _hallazgo.value = runCatching { contenedor.revisaCalidad.ejecuta() }
                .getOrDefault(emptyList())
                .firstOrNull { it.clave == clave }
            _cargando.value = false
        }
    }

    /** Solo se ofrecen las categorias que corresponden al signo del importe. */
    fun categoriasAplicables(todas: List<Categoria>, importeCentavos: Long): List<Categoria> {
        val tipos = if (importeCentavos < 0) listOf(TipoCategoria.GASTO, TipoCategoria.PATRIMONIO)
        else listOf(TipoCategoria.INGRESO)
        return todas.filter { it.padreId != null && it.tipo in tipos }
    }

    fun asignaCategoria(detalle: MovimientoDetallado, categoriaId: Long) {
        viewModelScope.launch {
            repo.guardaMovimiento(detalle.movimiento.copy(categoriaId = categoriaId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionPantalla(
    contenedor: Contenedor,
    clave: String,
    alAbrirMovimiento: (Long) -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("revision-$clave") { RevisionVm(contenedor, clave) }
    val hallazgo by vm.hallazgo.collectAsStateWithLifecycle()
    val cargando by vm.cargando.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.revisa() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    hallazgo?.titulo ?: "Revisar",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        when {
            cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            hallazgo == null -> EstadoVacio(
                icono = Icons.Filled.CheckCircle,
                titulo = "Ya quedo",
                detalle = "No queda ningun movimiento con este problema.",
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 48.dp)
            ) {
                item {
                    Text(
                        hallazgo?.detalle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(movimientos, key = { it.movimiento.id }) { detalle ->
                    Column(Modifier.fillMaxWidth()) {
                        RenglonMovimiento(detalle) { alAbrirMovimiento(detalle.movimiento.id) }
                        // Misma regla que usa la auditoria: ni las transferencias ni
                        // los movimientos internos llevan categoria.
                        if (detalle.movimiento.categoriaId == null &&
                            !detalle.movimiento.tipo.esTransferencia &&
                            !detalle.movimiento.tipo.esInterno
                        ) {
                            AsignadorCategoria(
                                opciones = vm.categoriasAplicables(
                                    categorias,
                                    detalle.movimiento.importeCentavos
                                ),
                                alElegir = { vm.asignaCategoria(detalle, it) }
                            )
                        }
                        HorizontalDivider(color = colores.trazoSuave)
                    }
                }
            }
        }
    }
}

/** Elegir categoria sin abrir el movimiento: clasificar cien renglones a mano cansa. */
@Composable
private fun AsignadorCategoria(
    opciones: List<Categoria>,
    alElegir: (Long) -> Unit
) {
    if (opciones.isEmpty()) return
    var abierto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { abierto = true }) { Text("Asignar categoria") }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { categoria ->
                DropdownMenuItem(
                    text = { Text(categoria.nombre) },
                    onClick = { alElegir(categoria.id); abierto = false }
                )
            }
        }
    }
}
