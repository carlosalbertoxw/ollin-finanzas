package mx.ollin.finanzas.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mx.ollin.finanzas.di.Contenedor
import mx.ollin.finanzas.ui.nav.Destino
import mx.ollin.finanzas.ui.nav.Rutas
import mx.ollin.finanzas.ui.screens.AcercaDePantalla
import mx.ollin.finanzas.ui.screens.AjustesPantalla
import mx.ollin.finanzas.ui.screens.AnaliticaPantalla
import mx.ollin.finanzas.ui.screens.ArchivoPantalla
import mx.ollin.finanzas.ui.screens.CalidadPantalla
import mx.ollin.finanzas.ui.screens.CapturaPantalla
import mx.ollin.finanzas.ui.screens.CategoriasPantalla
import mx.ollin.finanzas.ui.screens.CompromisosPantalla
import mx.ollin.finanzas.ui.screens.CuentasPantalla
import mx.ollin.finanzas.ui.screens.MovimientosPantalla
import mx.ollin.finanzas.ui.screens.PresupuestoPantalla
import mx.ollin.finanzas.ui.screens.RevisionPantalla
import mx.ollin.finanzas.ui.screens.TableroPantalla
import mx.ollin.finanzas.ui.screens.TransferenciaPantalla
import mx.ollin.finanzas.ui.screens.TutorialesPantalla

@Composable
fun OllinRaiz(contenedor: Contenedor) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    val destinoActual = remember(rutaActual) {
        Destino.entries.firstOrNull { it.ruta == rutaActual }
    }
    // El boton de captura solo tiene sentido sobre las pestañas principales.
    val muestraCaptura = destinoActual != null

    Scaffold(
        bottomBar = {
            if (destinoActual != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Destino.entries.forEach { destino ->
                        NavigationBarItem(
                            selected = destino == destinoActual,
                            onClick = {
                                nav.navigate(destino.ruta) {
                                    popUpTo(Destino.TABLERO.ruta) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destino.icono, contentDescription = destino.titulo) },
                            label = { Text(destino.titulo) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = muestraCaptura,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Rutas.captura()) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Capturar") }
                )
            }
        }
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno)) {
            NavHost(navController = nav, startDestination = Destino.TABLERO.ruta) {

                composable(Destino.TABLERO.ruta) {
                    TableroPantalla(
                        contenedor = contenedor,
                        alAbrirCuentas = { nav.navigate(Rutas.CUENTAS) },
                        alAbrirCalidad = { nav.navigate(Rutas.CALIDAD) },
                        alAbrirCompromisos = { nav.navigate(Rutas.COMPROMISOS) },
                        alAbrirAjustes = { nav.navigate(Rutas.AJUSTES) },
                        alAbrirTutoriales = { nav.navigate(Rutas.TUTORIALES) }
                    )
                }

                composable(Destino.MOVIMIENTOS.ruta) {
                    MovimientosPantalla(
                        contenedor = contenedor,
                        alAbrirMovimiento = { id -> nav.navigate(Rutas.captura(id)) },
                        alNuevaTransferencia = { nav.navigate(Rutas.transferencia()) }
                    )
                }

                composable(Destino.PRESUPUESTO.ruta) {
                    PresupuestoPantalla(
                        contenedor = contenedor,
                        alAbrirCategorias = { nav.navigate(Rutas.CATEGORIAS) }
                    )
                }

                composable(Destino.ANALITICA.ruta) {
                    AnaliticaPantalla(contenedor)
                }

                composable(Destino.ARCHIVO.ruta) {
                    ArchivoPantalla(
                        contenedor = contenedor,
                        alAbrirCalidad = { nav.navigate(Rutas.CALIDAD) }
                    )
                }

                composable(
                    route = Rutas.CAPTURA_CON_ID,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("compromiso") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { destino ->
                    CapturaPantalla(
                        contenedor = contenedor,
                        movimientoId = destino.arguments?.getLong("id")?.takeIf { it > 0L },
                        compromisoId = destino.arguments?.getLong("compromiso")?.takeIf { it > 0L },
                        alCerrar = { nav.popBackStack() },
                        alCambiarATransferencia = {
                            nav.popBackStack()
                            nav.navigate(Rutas.transferencia())
                        },
                        // Se saca la captura de la pila: al guardar, sus dos renglones
                        // se reescriben y el id que traia deja de existir.
                        alEditarTransferencia = { id ->
                            nav.popBackStack()
                            nav.navigate(Rutas.transferencia(id))
                        }
                    )
                }

                composable(
                    route = Rutas.TRANSFERENCIA_CON_ID,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { destino ->
                    TransferenciaPantalla(
                        contenedor = contenedor,
                        movimientoId = destino.arguments?.getLong("id")?.takeIf { it > 0L },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.CUENTAS) {
                    CuentasPantalla(contenedor) { nav.popBackStack() }
                }

                composable(Rutas.CATEGORIAS) {
                    CategoriasPantalla(contenedor) { nav.popBackStack() }
                }

                composable(Rutas.COMPROMISOS) {
                    CompromisosPantalla(
                        contenedor = contenedor,
                        alPagar = { id -> nav.navigate(Rutas.capturaDeCompromiso(id)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.CALIDAD) {
                    CalidadPantalla(
                        contenedor = contenedor,
                        alRevisarHallazgo = { clave -> nav.navigate(Rutas.revision(clave)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(
                    route = Rutas.REVISION,
                    arguments = listOf(navArgument("clave") { type = NavType.StringType })
                ) { destino ->
                    RevisionPantalla(
                        contenedor = contenedor,
                        clave = destino.arguments?.getString("clave").orEmpty(),
                        alAbrirMovimiento = { id -> nav.navigate(Rutas.captura(id)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.AJUSTES) {
                    AjustesPantalla(
                        contenedor = contenedor,
                        alAbrirCuentas = { nav.navigate(Rutas.CUENTAS) },
                        alAbrirCategorias = { nav.navigate(Rutas.CATEGORIAS) },
                        alAbrirCompromisos = { nav.navigate(Rutas.COMPROMISOS) },
                        alAbrirTutoriales = { nav.navigate(Rutas.TUTORIALES) },
                        alAbrirAcercaDe = { nav.navigate(Rutas.ACERCA_DE) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.TUTORIALES) {
                    TutorialesPantalla(
                        alCerrar = { nav.popBackStack() },
                        alAbrirAcercaDe = {
                            nav.navigate(Rutas.ACERCA_DE) { launchSingleTop = true }
                        }
                    )
                }

                composable(Rutas.ACERCA_DE) {
                    AcercaDePantalla(
                        // Sin launchSingleTop se apilarian Acerca de y Tutoriales una y
                        // otra vez al ir y venir entre las dos.
                        alAbrirTutoriales = {
                            nav.navigate(Rutas.TUTORIALES) { launchSingleTop = true }
                        },
                        alCerrar = { nav.popBackStack() }
                    )
                }
            }
        }
    }
}
