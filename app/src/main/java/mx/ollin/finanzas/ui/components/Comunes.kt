package mx.ollin.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ollin.finanzas.domain.model.Dinero
import mx.ollin.finanzas.ui.theme.EstiloCifra
import mx.ollin.finanzas.ui.theme.LocalColoresOllin
import kotlin.math.abs
import kotlin.math.max

/** Importe con color segun el signo y ancho tabular para que las columnas alineen. */
@Composable
fun TextoDinero(
    centavos: Long,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    coloreado: Boolean = true,
    enfasis: Boolean = false
) {
    val colores = LocalColoresOllin.current
    val color = when {
        !coloreado -> MaterialTheme.colorScheme.onSurface
        centavos > 0 -> colores.entrada
        centavos < 0 -> colores.salida
        else -> colores.textoTenue
    }
    Text(
        text = if (compacto) Dinero.formateaCompacto(centavos) else Dinero.formatea(centavos),
        modifier = modifier,
        style = EstiloCifra.copy(
            fontWeight = if (enfasis) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (enfasis) 20.sp else 15.sp
        ),
        color = color,
        maxLines = 1
    )
}

/** Tarjeta de indicador: una cifra grande con su etiqueta y una nota opcional. */
@Composable
fun TarjetaCifra(
    etiqueta: String,
    centavos: Long,
    modifier: Modifier = Modifier,
    nota: String? = null,
    coloreado: Boolean = false,
    acento: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (acento != null) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(acento)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalColoresOllin.current.textoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            TextoDinero(centavos, coloreado = coloreado, enfasis = true)
            if (nota != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    nota,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalColoresOllin.current.textoTenue
                )
            }
        }
    }
}

/** Tarjeta con un valor libre, para porcentajes y conteos. */
@Composable
fun TarjetaValor(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    nota: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = LocalColoresOllin.current.textoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(valor, style = MaterialTheme.typography.headlineSmall, color = color)
            if (nota != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    nota,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalColoresOllin.current.textoTenue
                )
            }
        }
    }
}

/**
 * Barras mensuales de ingreso contra gasto. Dos series enfrentadas sobre la
 * misma linea base para que la diferencia sea el mensaje, no la altura.
 */
@Composable
fun BarrasFlujo(
    ingresos: List<Long>,
    gastos: List<Long>,
    etiquetas: List<String>,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    val maximo = max(
        ingresos.maxOfOrNull { abs(it) } ?: 1L,
        gastos.maxOfOrNull { abs(it) } ?: 1L
    ).coerceAtLeast(1L)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val n = max(ingresos.size, gastos.size)
            if (n == 0) return@Canvas
            val anchoRanura = size.width / n
            val anchoBarra = (anchoRanura * 0.34f).coerceAtMost(18f.dp.toPx())
            val separacion = anchoBarra * 0.18f

            ingresos.indices.forEach { i ->
                val centro = anchoRanura * i + anchoRanura / 2f
                val altoIngreso = (abs(ingresos[i]).toFloat() / maximo) * size.height
                val altoGasto = (abs(gastos.getOrElse(i) { 0L }).toFloat() / maximo) * size.height

                drawRoundRect(
                    color = colores.entrada,
                    topLeft = Offset(centro - anchoBarra - separacion, size.height - altoIngreso),
                    size = Size(anchoBarra, altoIngreso),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = colores.salida,
                    topLeft = Offset(centro + separacion, size.height - altoGasto),
                    size = Size(anchoBarra, altoGasto),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            etiquetas.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoTenue,
                    maxLines = 1
                )
            }
        }
    }
}

/** Linea de evolucion, para el patrimonio. */
@Composable
fun LineaEvolucion(
    valores: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (valores.size < 2) return
    Canvas(modifier) {
        val minimo = valores.min().toFloat()
        val maximo = valores.max().toFloat()
        val rango = (maximo - minimo).takeIf { it > 0f } ?: 1f
        val paso = size.width / (valores.size - 1)

        val trazo = Path()
        val relleno = Path()
        valores.forEachIndexed { i, v ->
            val x = paso * i
            val y = size.height - ((v.toFloat() - minimo) / rango) * size.height * 0.9f - size.height * 0.05f
            if (i == 0) { trazo.moveTo(x, y); relleno.moveTo(x, size.height); relleno.lineTo(x, y) }
            else { trazo.lineTo(x, y); relleno.lineTo(x, y) }
        }
        relleno.lineTo(size.width, size.height)
        relleno.close()

        drawPath(relleno, color = color.copy(alpha = 0.14f))
        drawPath(trazo, color = color, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Barra de avance de presupuesto, con semaforo. */
@Composable
fun BarraAvance(
    avance: Double,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    val color = when {
        avance > 1.0 -> colores.salida
        avance > 0.85 -> colores.alerta
        else -> colores.entrada
    }
    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colores.trazoSuave)
    ) {
        Box(
            Modifier
                .fillMaxWidth(avance.coerceIn(0.0, 1.0).toFloat())
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier,
    accion: @Composable (() -> Unit)? = null
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = LocalColoresOllin.current.textoTenue,
            modifier = Modifier.size(44.dp)
        )
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresOllin.current.textoTenue
        )
        accion?.invoke()
    }
}

/** Punto de color de una cuenta o categoria. */
@Composable
fun Punto(color: Color, tamano: Int = 10) {
    Box(
        Modifier
            .size(tamano.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SeccionTitulo(texto: String, modifier: Modifier = Modifier, accion: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
        accion?.invoke()
    }
}

/** Contenedor con borde suave, para agrupar sin recurrir a otra tarjeta. */
@Composable
fun Marco(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LocalColoresOllin.current.trazoSuave, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) { contenido() }
}
