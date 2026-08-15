package mx.ollin.finanzas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mx.ollin.finanzas.ui.theme.LocalColoresOllin

/**
 * Un tutorial: para que sirve algo y en que orden se hace.
 *
 * Los pasos son texto plano y no capturas de pantalla a proposito: una captura
 * envejece con el primer cambio de la interfaz y despues miente, mientras que el
 * orden de los pasos sigue siendo cierto.
 */
data class Tutorial(
    val clave: String,
    val titulo: String,
    val resumen: String,
    val icono: ImageVector,
    val pasos: List<String>,
    /** Lo que casi nadie descubre solo y evita el error mas comun del tema. */
    val truco: String? = null
)

val TUTORIALES: List<Tutorial> = listOf(
    Tutorial(
        clave = "primeros-pasos",
        titulo = "Primeros pasos",
        resumen = "Dejar la app lista antes de capturar el primer movimiento.",
        icono = Icons.Filled.AccountBalanceWallet,
        pasos = listOf(
            "Abre Ajustes con el engrane del tablero y entra a Administrar cuentas. " +
                "Da de alta las que de verdad usas: tu nomina, tu tarjeta, el efectivo.",
            "Entra a Administrar categorias y ajusta el catalogo a tu vida. Las que " +
                "trae de fabrica son un punto de partida, no una regla.",
            "Captura el saldo de apertura de cada cuenta con el tipo \"Saldo inicial\", " +
                "para que los saldos arranquen donde de verdad estan.",
            "Cuando ya tengas tus saldos iniciales, apaga \"Mostrar Saldo inicial\" en " +
                "Ajustes: la captura de diario queda mas corta."
        ),
        truco = "Marca como fuera del patrimonio las cuentas con dinero que pasa por tus " +
            "manos pero no es tuyo. Se siguen registrando sin inflar tu patrimonio."
    ),
    Tutorial(
        clave = "captura",
        titulo = "Capturar un movimiento",
        resumen = "Lo que entra y lo que sale, en menos de treinta segundos.",
        icono = Icons.Filled.NoteAdd,
        pasos = listOf(
            "Toca el boton Capturar, que esta en cualquiera de las cinco pestañas de abajo.",
            "Elige el tipo: entrada si el dinero llega, salida si se va.",
            "Escribe el importe. Se guarda en centavos exactos, asi que no hay redondeos raros.",
            "Elige la cuenta y la categoria de las listas. No se escriben a mano a proposito: " +
                "asi \"Super\" y \"super \" no acaban siendo dos rubros distintos.",
            "En la descripcion pon el detalle: la tienda, el concepto, el nombre del cliente."
        ),
        truco = "La categoria clasifica y la descripcion detalla, y son cosas distintas. " +
            "Cinco descripciones dentro de una misma categoria se suman como el rubro " +
            "grande que son."
    ),
    Tutorial(
        clave = "transferencias",
        titulo = "Mover dinero entre tus cuentas",
        resumen = "Una sola captura que genera los dos renglones ligados.",
        icono = Icons.Filled.SwapHoriz,
        pasos = listOf(
            "Entra a Movimientos y usa Nueva transferencia.",
            "Elige la cuenta de origen y la de destino, y el importe una sola vez.",
            "Al guardar se escriben dos renglones ligados por un mismo grupo."
        ),
        truco = "Borrar uno de los dos renglones borra el otro, asi que nunca te queda media " +
            "transferencia descuadrando los saldos. Pagar la tarjeta con la nomina es " +
            "una transferencia, no un gasto: el gasto ya se registro cuando compraste."
    ),
    Tutorial(
        clave = "patrimonio",
        titulo = "Gasto contra patrimonio",
        resumen = "Por que comprar un terreno no aparece como gasto.",
        icono = Icons.Filled.Sell,
        pasos = listOf(
            "Las categorias tienen tipo. Las de tipo Patrimonio no cuentan como consumo.",
            "Las cuentas tienen tipo. Las de tipo Activo guardan valor en vez de dinero liquido.",
            "Comprar cripto, un terreno o un celular caro es trasladar patrimonio de una " +
                "cuenta a otra, no gastarlo: registralo con categoria de Patrimonio."
        ),
        truco = "Por eso la grafica de Ingresos contra gasto y la tasa de ahorro no se " +
            "desploman el mes que compraste algo grande."
    ),
    Tutorial(
        clave = "presupuesto",
        titulo = "Presupuesto y analitica",
        resumen = "Poner una meta por categoria y ver si la cumpliste.",
        icono = Icons.Filled.AccountBalanceWallet,
        pasos = listOf(
            "Entra a la pestaña Presupuesto y pon un monto meta por categoria.",
            "La barra se pone ambar cuando te acercas y roja cuando te pasas.",
            "En la pestaña Analitica ves la tendencia mensual y en que se te fue el dinero."
        ),
        truco = "El tablero calcula tu fondo de emergencia con el gasto promedio, sin contar " +
            "el mes en curso: un mes a medias jalaria el promedio hacia abajo."
    ),
    Tutorial(
        clave = "compromisos",
        titulo = "Compromisos que aun no pagas",
        resumen = "Mensualidades, suscripciones y gastos anuales, con aviso.",
        icono = Icons.Filled.EventRepeat,
        pasos = listOf(
            "Entra a Ajustes y luego a Administrar compromisos.",
            "Da de alta cada uno con su cuenta, categoria, periodicidad y fecha del " +
                "siguiente pago.",
            "La app revisa una vez al dia y te avisa antes de que venza.",
            "Cuando toque, usa Registrar: se abre la captura ya llena para que corrijas el " +
                "monto si cambio.",
            "Desliza la tarjeta a la derecha y elige Cumplir, si el pago ya se hizo, o " +
                "Descartar, si este periodo no se cobro."
        ),
        truco = "Nada avanza solo: mientras no cumplas ni descartes, el pago sigue " +
            "pendiente aunque se pase de fecha. Descartar recorre la fecha sin contar el " +
            "pago, asi que no acorta unos meses sin intereses, y las dos decisiones se " +
            "deshacen desde el aviso que sale abajo."
    ),
    Tutorial(
        clave = "calidad",
        titulo = "Salud de los datos",
        resumen = "La pantalla que encuentra lo que quedo mal capturado.",
        icono = Icons.Filled.HealthAndSafety,
        pasos = listOf(
            "Cuando hay algo que revisar, el tablero lo muestra hasta arriba.",
            "Cada hallazgo dice que esta mal: un tipo que contradice el signo del importe, " +
                "una transferencia sin su pareja, un movimiento sin categoria, o dos " +
                "descripciones casi identicas.",
            "Varios se arreglan con un boton; los demas te llevan al movimiento para que " +
                "decidas tu."
        ),
        truco = "Si los datos estan mal, todo lo demas miente. Por eso los hallazgos van " +
            "arriba de las cifras del tablero y no escondidos en un menu."
    ),
    Tutorial(
        clave = "respaldo",
        titulo = "Respaldar con Excel",
        resumen = "Tu respaldo es un .xlsx que tu decides donde guardar.",
        icono = Icons.Filled.SwapVert,
        pasos = listOf(
            "Entra a la pestaña Archivo.",
            "Elige el esquema: Extendido para trabajar dentro de la app, Compacto para " +
                "las ocho columnas esenciales.",
            "Elige que pestañas generar. Registros siempre va, porque es la fuente de las " +
                "formulas de todas las demas.",
            "Exporta y guarda el archivo donde quieras: memoria del telefono, OneDrive, Drive.",
            "Para volver a entrar, usa Importar y elige el .xlsx."
        ),
        truco = "El respaldo automatico de Android esta desactivado para la base a proposito. " +
            "Exporta seguido: es el unico respaldo que existe."
    ),
    Tutorial(
        clave = "bloqueo",
        titulo = "Poner llave a la app",
        resumen = "Que nadie mas vea tus numeros si toma tu telefono.",
        icono = Icons.Filled.Lock,
        pasos = listOf(
            "Entra a Ajustes y busca la seccion Bloqueo.",
            "Del telefono usa tu patron, PIN o huella, y la app no guarda ningun secreto.",
            "PIN propio usa uno solo de la app, distinto al del telefono.",
            "Cambiar o quitar el candado siempre pide antes la llave que tienes puesta."
        ),
        truco = "Si eliges PIN propio y lo olvidas no hay forma de recuperarlo: tendrias que " +
            "reinstalar la app y perderias los datos que no hayas exportado."
    )
)

/**
 * Los tutoriales, uno por tarjeta desplegable.
 *
 * Se abre solo uno a la vez: con nueve temas, dejarlos todos abiertos convierte
 * la pantalla en un muro de texto que ya nadie lee.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialesPantalla(
    alCerrar: () -> Unit,
    alAbrirAcercaDe: (() -> Unit)? = null
) {
    val colores = LocalColoresOllin.current
    var abierto by rememberSaveable { mutableStateOf<String?>(TUTORIALES.first().clave) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tutoriales") },
            navigationIcon = {
                IconButton(onClick = alCerrar) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Como se usa la app, tema por tema. Toca uno para ver los pasos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoTenue
                )
            }

            items(TUTORIALES, key = { it.clave }) { tutorial ->
                TarjetaTutorial(
                    tutorial = tutorial,
                    expandido = abierto == tutorial.clave,
                    alTocar = { abierto = if (abierto == tutorial.clave) null else tutorial.clave }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Puedes apagar los tutoriales en Ajustes cuando ya no los ocupes. " +
                        "Esta pantalla se sigue abriendo desde ahi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                if (alAbrirAcercaDe != null) {
                    TextButton(onClick = alAbrirAcercaDe, modifier = Modifier.fillMaxWidth()) {
                        Text("Acerca de Ollin Finanzas")
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaTutorial(
    tutorial: Tutorial,
    expandido: Boolean,
    alTocar: () -> Unit
) {
    val colores = LocalColoresOllin.current

    Card(
        Modifier.fillMaxWidth().clickable(onClick = alTocar),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    tutorial.icono,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(tutorial.titulo, style = MaterialTheme.typography.titleSmall)
                    Text(
                        tutorial.resumen,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Icon(
                    if (expandido) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expandido) "Contraer" else "Desplegar",
                    tint = colores.textoTenue
                )
            }

            AnimatedVisibility(visible = expandido) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colores.trazoSuave)
                    Spacer(Modifier.height(12.dp))

                    tutorial.pasos.forEachIndexed { i, paso ->
                        Row(
                            Modifier.padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colores.textoTenue
                            )
                            Text(paso, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    tutorial.truco?.let {
                        Spacer(Modifier.height(2.dp))
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Ojo", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
