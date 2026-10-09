package com.carlosalbertoxw.ollin.finanzas.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.finanzas.data.db.Categoria
import com.carlosalbertoxw.ollin.finanzas.data.db.FlujoMes
import com.carlosalbertoxw.ollin.finanzas.data.db.MovimientoDetallado
import com.carlosalbertoxw.ollin.finanzas.data.repo.FinanzasRepositorio
import com.carlosalbertoxw.ollin.finanzas.domain.model.Dinero
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoCategoria
import com.carlosalbertoxw.ollin.finanzas.domain.model.TipoMovimiento
import com.carlosalbertoxw.ollin.finanzas.ui.components.BarrasFlujo
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaCifra
import com.carlosalbertoxw.ollin.finanzas.ui.recuerdaVm
import com.carlosalbertoxw.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

data class GrupoGasto(
    val nombre: String,
    val totalCentavos: Long,
    val porcentaje: Double,
    val esPatrimonio: Boolean
)
class AnaliticaVm(private val repo: FinanzasRepositorio) : ViewModel() {

    val flujo: StateFlow<List<FlujoMes>> = repo.observaFlujoMensual()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Agrupa por categoria padre. Es el corte que hace visible un rubro grande
     * repartido en varias descripciones distintas, que suelto no se nota.
     */
    val grupos: StateFlow<List<GrupoGasto>> = combine(
        repo.observaMovimientos(incluyeTraspasos = false, limite = 20_000),
        repo.observaCategorias()
    ) { movimientos, categorias ->
        construyeGrupos(movimientos, categorias)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun construyeGrupos(
        movimientos: List<MovimientoDetallado>,
        categorias: List<Categoria>
    ): List<GrupoGasto> {
        val porId = categorias.associateBy { it.id }
        val salidas = movimientos.filter { it.movimiento.tipo == TipoMovimiento.SALIDA }
        if (salidas.isEmpty()) return emptyList()

        val agrupado = salidas.groupBy { detalle ->
            val categoria = detalle.movimiento.categoriaId?.let { porId[it] }
            val padre = categoria?.padreId?.let { porId[it] } ?: categoria
            (padre?.nombre ?: "Sin categoria") to (padre?.tipo == TipoCategoria.PATRIMONIO)
        }.mapValues { (_, lista) -> lista.sumOf { it.movimiento.importeCentavos } }

        // El porcentaje se mide contra el consumo real, no contra el total que
        // incluye compras de patrimonio: mezclarlos distorsiona la lectura.
        val consumo = agrupado.filterKeys { !it.second }.values.sumOf { abs(it) }

        return agrupado.entries
            .sortedBy { it.value }
            .map { (clave, total) ->
                GrupoGasto(
                    nombre = clave.first,
                    totalCentavos = total,
                    porcentaje = if (clave.second || consumo == 0L) {
                        0.0
                    } else {
                        abs(total).toDouble() / consumo
                    },
                    esPatrimonio = clave.second
                )
            }
    }
}
