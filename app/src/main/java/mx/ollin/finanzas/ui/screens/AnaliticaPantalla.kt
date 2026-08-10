package mx.ollin.finanzas.ui.screens

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mx.ollin.finanzas.data.db.Categoria
import mx.ollin.finanzas.data.db.FlujoMes
import mx.ollin.finanzas.data.db.MovimientoDetallado
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.domain.model.TipoCategoria
import mx.ollin.finanzas.domain.model.TipoMovimiento
import mx.ollin.finanzas.ui.components.BarrasFlujo
import mx.ollin.finanzas.ui.components.EstadoVacio
import mx.ollin.finanzas.ui.components.SeccionTitulo
import mx.ollin.finanzas.ui.components.TarjetaCifra
import mx.ollin.finanzas.ui.recuerdaVm
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlin.math.abs

data class GrupoGasto(
    val nombre: String,
    val totalCentavos: Long,
    val porcentaje: Double,
    val esPatrimonio: Boolean
)

class AnaliticaVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

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
                    porcentaje = if (clave.second || consumo == 0L) 0.0 else abs(total).toDouble() / consumo,
                    esPatrimonio = clave.second
                )
            }
    }
}

@Composable
fun AnaliticaPantalla(contenedor: Contenedor) {
    val vm = recuerdaVm("analitica") { AnaliticaVm(contenedor) }
    val flujo by vm.flujo.collectAsStateWithLifecycle()
    val grupos by vm.grupos.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    if (flujo.isEmpty()) {
        EstadoVacio(
            icono = Icons.Filled.Insights,
            titulo = "Aun no hay que analizar",
            detalle = "Importa tu Excel o captura unos movimientos y aqui aparece la lectura.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val ingresos = flujo.sumOf { it.ingresosCentavos }
    val consumo = flujo.sumOf { it.gastoConsumoCentavos }
    val patrimonio = flujo.sumOf { it.compraPatrimonioCentavos }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Analitica", style = MaterialTheme.typography.headlineSmall) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaCifra("Ingresos", ingresos, Modifier.weight(1f), acento = colores.entrada)
                TarjetaCifra("Consumo", consumo, Modifier.weight(1f), acento = colores.salida)
            }
        }

        if (patrimonio != 0L) {
            item {
                TarjetaCifra(
                    etiqueta = "Compra de patrimonio",
                    centavos = patrimonio,
                    modifier = Modifier.fillMaxWidth(),
                    nota = "No es gasto: el dinero cambio de forma, no desaparecio.",
                    acento = colores.patrimonio
                )
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    SeccionTitulo("Mes a mes")
                    Spacer(Modifier.height(12.dp))
                    val ultimos = flujo.takeLast(8)
                    BarrasFlujo(
                        ingresos = ultimos.map { it.ingresosCentavos },
                        gastos = ultimos.map { it.gastoConsumoCentavos },
                        etiquetas = ultimos.map { it.periodo.takeLast(2) }
                    )
                }
            }
        }

        item { SeccionTitulo("Tasa de ahorro por mes") }
        items(flujo.reversed(), key = { "mes-${it.periodo}" }) { mes ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(mes.periodo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(58.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colores.trazoSuave)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(mes.tasaAhorro.coerceIn(0.0, 1.0).toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (mes.tasaAhorro >= 0.2) colores.entrada else colores.alerta)
                    )
                }
                Text(
                    "%.0f%%".format(mes.tasaAhorro * 100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoTenue
                )
            }
        }

        item { SeccionTitulo("Donde se va el dinero") }
        items(grupos, key = { "grupo-${it.nombre}" }) { grupo ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        grupo.nombre + if (grupo.esPatrimonio) "  (patrimonio)" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        Dinero.formateaCorto(abs(grupo.totalCentavos)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colores.trazoSuave)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(grupo.porcentaje.coerceIn(0.0, 1.0).toFloat())
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (grupo.esPatrimonio) colores.patrimonio else colores.salida)
                    )
                }
                if (!grupo.esPatrimonio) {
                    Text(
                        "%.1f%% del consumo".format(grupo.porcentaje * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
        }
    }
}
