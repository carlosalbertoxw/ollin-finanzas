package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDetallado
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin

/** Como se lee no tener filtro de cuenta. Es opcion del menu y texto del campo. */
private const val TODAS_LAS_CUENTAS = "Todas las cuentas"

data class FiltroMovimientos(
    val texto: String = "",
    val cuentaId: Long? = null,
    val categoriaId: Long? = null,
    val incluyeTraspasos: Boolean = false
)

class MovimientosVm(private val repo: FinanzasRepositorio) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroMovimientos())
    val filtro: StateFlow<FiltroMovimientos> = _filtro

    val cuentas: StateFlow<List<Cuenta>> = repo.observaCuentas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Cuantos renglones se piden ahora mismo. Crece de [PAGINA] en [PAGINA] al
     * llegar al final de la lista y vuelve al principio en cuanto cambia el
     * filtro: pedir mil renglones de un filtro que ya no esta en pantalla es
     * trabajo tirado.
     */
    private val _limite = MutableStateFlow(PAGINA)

    @OptIn(ExperimentalCoroutinesApi::class)
    val movimientos: StateFlow<List<MovimientoDetallado>> =
        combine(_filtro, _limite) { f, limite -> f to limite }
            .flatMapLatest { (f, limite) ->
                repo.observaMovimientos(
                    cuentaId = f.cuentaId,
                    categoriaId = f.categoriaId,
                    incluyeTraspasos = f.incluyeTraspasos,
                    texto = f.texto,
                    limite = limite
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Cuantos cumplen el filtro en total, no cuantos se alcanzaron a cargar.
     * Es lo que dice si queda algo mas abajo.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val totalDelFiltro: StateFlow<Int> = _filtro
        .flatMapLatest { f ->
            repo.observaConteoFiltrado(
                cuentaId = f.cuentaId,
                categoriaId = f.categoriaId,
                incluyeTraspasos = f.incluyeTraspasos,
                texto = f.texto
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Total de lo que cumple el filtro, para que el filtro sirva de calculadora.
     *
     * Se suma en SQL y no sobre la lista cargada. Sumando la pagina, un filtro
     * con mas renglones de los que caben daba una cifra parcial presentada como
     * si fuera el total: una calculadora que miente es peor que ninguna.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val totalVisible: StateFlow<Long> = _filtro
        .flatMapLatest { f ->
            repo.observaTotalFiltrado(
                cuentaId = f.cuentaId,
                categoriaId = f.categoriaId,
                incluyeTraspasos = f.incluyeTraspasos,
                texto = f.texto
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun actualiza(bloque: (FiltroMovimientos) -> FiltroMovimientos) {
        _filtro.value = bloque(_filtro.value)
        _limite.value = PAGINA
    }

    fun cargaMas() {
        _limite.value += PAGINA
    }

    private companion object {
        const val PAGINA = 200
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosPantalla(
    repo: FinanzasRepositorio,
    alAbrirMovimiento: (Long) -> Unit,
    alNuevaTransferencia: () -> Unit,
    /**
     * Cuenta que el tablero pide filtrar al llegar, o nulo si se entro por la
     * pestaña. Es un recado de un solo uso: en cuanto se aplica se devuelve con
     * [alAplicarCuentaInicial] para que volver a esta pestaña mas tarde no
     * reimponga un filtro que el usuario ya quito.
     */
    cuentaInicial: Long? = null,
    alAplicarCuentaInicial: () -> Unit = {}
) {
    val vm = recuerdaVm("movimientos") { MovimientosVm(repo) }

    LaunchedEffect(cuentaInicial) {
        if (cuentaInicial != null) {
            vm.actualiza { it.copy(cuentaId = cuentaInicial) }
            alAplicarCuentaInicial()
        }
    }

    val filtro by vm.filtro.collectAsStateWithLifecycle()
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val cuentas by vm.cuentas.collectAsStateWithLifecycle()
    val total by vm.totalVisible.collectAsStateWithLifecycle()
    val totalDelFiltro by vm.totalDelFiltro.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current
    val estadoLista = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
            OutlinedTextField(
                value = filtro.texto,
                onValueChange = { texto -> vm.actualiza { it.copy(texto = texto) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar descripcion o nota") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Desplegable y no un chip que cicla: con ocho cuentas, llegar a
                // la ultima costaba ocho toques y pasarse obligaba a dar la
                // vuelta entera. Aqui se ve la lista completa y se elige de una.
                SelectorDesplegable(
                    etiqueta = "Cuenta",
                    valor = cuentas.firstOrNull { it.id == filtro.cuentaId }?.nombre
                        ?: TODAS_LAS_CUENTAS,
                    // La primera opcion es la salida: sin ella, quitar el filtro
                    // de cuenta seria justo lo que el desplegable no deja hacer.
                    opciones = listOf(null to TODAS_LAS_CUENTAS) +
                        cuentas.map { it.id to it.nombre },
                    alElegir = { id -> vm.actualiza { it.copy(cuentaId = id) } },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filtro.incluyeTraspasos,
                    onClick = { vm.actualiza { it.copy(incluyeTraspasos = !it.incluyeTraspasos) } },
                    label = { Text("Traspasos") },
                    leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${movimientos.size} movimientos",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Text(
                    "Suma: ${Dinero.formatea(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (total < 0) colores.salida else colores.entrada
                )
            }
        }

        if (movimientos.isEmpty()) {
            EstadoVacio(
                icono = Icons.Filled.ReceiptLong,
                titulo = "Nada por aqui",
                detalle = "Ajusta el filtro, captura un movimiento o importa tu Excel desde " +
                    "Ajustes > Importar y exportar.",
                modifier = Modifier.fillMaxWidth()
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            state = estadoLista,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)
        ) {
            val porFecha = movimientos.groupBy { it.movimiento.fecha }
            porFecha.forEach { (fecha, delDia) ->
                item(key = "cab-$fecha") {
                    SeccionTitulo(fecha.toString(), Modifier.padding(vertical = 8.dp)) {
                        Text(
                            Dinero.formatea(delDia.sumOf { it.movimiento.importeCentavos }),
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                }
                items(delDia, key = { it.movimiento.id }) { detalle ->
                    RenglonMovimiento(detalle) { alAbrirMovimiento(detalle.movimiento.id) }
                }
            }

            // Lo que falta por cargar se dice y se puede pedir. Sin este
            // renglon la lista se cortaria en seco y los movimientos viejos no
            // existirian para quien mira esta pantalla.
            if (movimientos.size < totalDelFiltro) {
                item(key = "cargar-mas") {
                    TextButton(
                        onClick = vm::cargaMas,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Ver mas (${totalDelFiltro - movimientos.size} restantes)")
                    }
                }
            }

            item {
                TextButton(onClick = alNuevaTransferencia, modifier = Modifier.padding(top = 12.dp)) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Text("  Nueva transferencia entre cuentas")
                }
            }
        }
    }
}
