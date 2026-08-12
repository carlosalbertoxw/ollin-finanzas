package mx.ollin.finanzas.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import mx.ollin.finanzas.R
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

/**
 * Quien es la app, de donde viene el nombre y como trata tus datos.
 *
 * Vive aparte de Ajustes a proposito: aqui no se cambia nada, solo se lee, y
 * mezclar lectura con interruptores alarga la pantalla que si se usa a diario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaDePantalla(
    alAbrirTutoriales: () -> Unit,
    alCerrar: () -> Unit
) {
    val contexto = LocalContext.current
    val colores = LocalColoresOllin.current

    // Se lee del paquete instalado y no de BuildConfig: asi la version que se
    // muestra es la que el telefono tiene puesta, sin activar buildConfig.
    val version = remember(contexto) {
        runCatching {
            contexto.packageManager.getPackageInfo(contexto.packageName, 0)
        }.getOrNull()?.let { info ->
            val nombre = info.versionName ?: "1.0"
            val codigo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            "Version $nombre  ·  build $codigo"
        } ?: "Version desconocida"
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Acerca de") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ollin_glyph),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text("Ollin Finanzas", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "El libro de tus movimientos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.textoTenue
                    )
                    Text(
                        version,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }

            HorizontalDivider()

            Text("De donde viene el nombre", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ollin es \"movimiento\" en nahuatl, y es el nombre del glifo del calendario " +
                    "mexica que representa el cambio. Un libro de finanzas no es mas que el " +
                    "registro de tus movimientos, y de ahi sale tambien el icono: dos listones " +
                    "cruzados, el jade para lo que entra y la grana para lo que sale, con el " +
                    "saldo en el centro.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Que hace", style = MaterialTheme.typography.titleMedium)
            Vineta(
                "Registra entradas, salidas, transferencias y saldos de apertura, con " +
                    "cuentas y categorias de listas cerradas."
            )
            Vineta(
                "Separa el patrimonio del consumo: comprar un terreno o cripto es trasladar " +
                    "patrimonio, no gastarlo, y los tableros lo cuentan aparte."
            )
            Vineta(
                "Vigila la salud de tus datos y repara con un boton lo que se puede reparar solo."
            )
            Vineta(
                "Lleva presupuesto por categoria, tendencia mensual y compromisos por vencer."
            )
            Vineta("Importa y exporta libros .xlsx que abren igual en Excel, WPS, LibreOffice y Sheets.")

            HorizontalDivider()

            Text("Tu privacidad", style = MaterialTheme.typography.titleMedium)
            Text(
                "Todo vive en este telefono. No hay cuentas de usuario, ni servidor, ni " +
                    "analitica, ni publicidad: nada sale de aqui salvo el .xlsx que tu " +
                    "exportas a donde tu decides.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Text(
                "La base de datos esta cifrada con AES-256 y su llave vive en el Keystore del " +
                    "telefono. El respaldo automatico del sistema esta desactivado para la base " +
                    "a proposito: tu respaldo es la exportacion a .xlsx.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("Como estan hechas las cuentas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los importes se guardan en centavos enteros, nunca en decimales flotantes: " +
                    "por eso un saldo en cero es exactamente cero y las conciliaciones cuadran.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Text(
                "Las hojas de analisis se exportan con formulas vivas en vez de tablas " +
                    "dinamicas, para que se recalculen solas en cualquier suite de oficina.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            HorizontalDivider()

            Text("¿Apenas empiezas?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Los tutoriales explican paso a paso la captura, las transferencias, el " +
                    "presupuesto y el respaldo.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            TextButton(onClick = alAbrirTutoriales, modifier = Modifier.fillMaxWidth()) {
                Text("Ver los tutoriales")
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}

/** Renglon de vineta. Se repite lo suficiente para no escribirlo a mano cada vez. */
@Composable
private fun Vineta(texto: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium, color = LocalColoresOllin.current.textoTenue)
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresOllin.current.textoTenue
        )
    }
}
