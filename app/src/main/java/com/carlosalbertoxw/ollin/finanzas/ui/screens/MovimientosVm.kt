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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.Cuenta
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDetallado
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

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
    private val limiteActual = MutableStateFlow(PAGINA)

    @OptIn(ExperimentalCoroutinesApi::class)
    val movimientos: StateFlow<List<MovimientoDetallado>> =
        combine(_filtro, limiteActual) { f, limite -> f to limite }
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
        limiteActual.value = PAGINA
    }

    fun cargaMas() {
        limiteActual.value += PAGINA
    }

    private companion object {
        const val PAGINA = 200
    }
}
