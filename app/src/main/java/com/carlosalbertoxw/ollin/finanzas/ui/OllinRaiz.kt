package com.carlosalbertoxw.ollin.finanzas.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carlosalbertoxw.ollin.finanzas.di.Contenedor
import com.carlosalbertoxw.ollin.finanzas.ui.nav.Destino
import com.carlosalbertoxw.ollin.finanzas.ui.nav.Rutas
import com.carlosalbertoxw.ollin.finanzas.ui.screens.AcercaDePantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.AjustesPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.AnaliticaPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.ArchivoPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.CalidadPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.CapturaPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.CategoriasPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.CompromisosPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.CuentasPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.MovimientosPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.PresupuestoPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.RevisionPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.TableroPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.TransferenciaPantalla
import com.carlosalbertoxw.ollin.finanzas.ui.screens.TutorialesPantalla

@Composable
fun OllinRaiz(contenedor: Contenedor, rutaInicial: String? = null) {
    // Unico lugar que conoce el contenedor entero. De aqui hacia abajo cada
    // pantalla recibe solo lo que usa, y asi su ViewModel se puede construir en
    // una prueba sin levantar la base cifrada ni DataStore.
    val repo = contenedor.repositorio
    val ajustes = contenedor.ajustes
    val revisaCalidad = contenedor.revisaCalidad
    val reparaDatos = contenedor.reparaDatos

    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    val destinoActual = remember(rutaActual) {
        Destino.entries.firstOrNull { it.ruta == rutaActual }
    }

    /**
     * Cuenta que Movimientos debe filtrar al abrirse, puesta por el tablero al
     * tocar un saldo.
     *
     * Es un recado entre destinos y no un argumento de navegacion porque la
     * barra de abajo guarda y restaura el estado de cada pestaña: un argumento
     * solo viajaria la primera vez, y en las siguientes lo pisaria el estado
     * restaurado. Vive aqui, por encima del NavHost, para sobrevivir al salto.
     */
    var cuentaParaMovimientos by rememberSaveable { mutableStateOf<Long?>(null) }

    /** Ir a una pestaña como si se tocara su boton, con su estado guardado. */
    fun vaAPestana(destino: Destino) {
        nav.navigate(destino.ruta) {
            popUpTo(Destino.TABLERO.ruta) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
                            onClick = { vaAPestana(destino) },
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
            // La ruta inicial solo puede ser una de las pestanas: llega de una
            // notificacion, y una ruta desconocida --o una con argumentos-- no
            // puede acabar en una pantalla sin forma de volver.
            val arranque = Destino.entries
                .firstOrNull { it.ruta == rutaInicial }
                ?.ruta
                ?: Destino.TABLERO.ruta

            NavHost(navController = nav, startDestination = arranque) {

                composable(Destino.TABLERO.ruta) {
                    TableroPantalla(
                        repo = repo,
                        ajustes = ajustes,
                        revisaCalidad = revisaCalidad,
                        alAbrirCuentas = { nav.navigate(Rutas.CUENTAS) },
                        alAbrirMovimientosDeCuenta = { id ->
                            cuentaParaMovimientos = id
                            vaAPestana(Destino.MOVIMIENTOS)
                        },
                        alAbrirCalidad = { nav.navigate(Rutas.CALIDAD) },
                        alAbrirCompromisos = { nav.navigate(Rutas.COMPROMISOS) },
                        alPagarCompromiso = { id -> nav.navigate(Rutas.capturaDeCompromiso(id)) },
                        alAbrirAjustes = { nav.navigate(Rutas.AJUSTES) },
                        alAbrirTutoriales = { nav.navigate(Rutas.TUTORIALES) }
                    )
                }

                composable(Destino.MOVIMIENTOS.ruta) {
                    MovimientosPantalla(
                        repo = repo,
                        alAbrirMovimiento = { id -> nav.navigate(Rutas.captura(id)) },
                        alNuevaTransferencia = { nav.navigate(Rutas.transferencia()) },
                        cuentaInicial = cuentaParaMovimientos,
                        alAplicarCuentaInicial = { cuentaParaMovimientos = null }
                    )
                }

                composable(Destino.PRESUPUESTO.ruta) {
                    PresupuestoPantalla(
                        repo = repo,
                        alAbrirCategorias = { nav.navigate(Rutas.CATEGORIAS) }
                    )
                }

                composable(Destino.ANALITICA.ruta) {
                    AnaliticaPantalla(repo)
                }

                composable(Rutas.ARCHIVO) {
                    ArchivoPantalla(
                        repo = repo,
                        ajustes = ajustes,
                        revisaCalidad = revisaCalidad,
                        alAbrirCalidad = { nav.navigate(Rutas.CALIDAD) },
                        alCerrar = { nav.popBackStack() }
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
                        repo = repo,
                        ajustes = ajustes,
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
                        repo = repo,
                        movimientoId = destino.arguments?.getLong("id")?.takeIf { it > 0L },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.CUENTAS) {
                    CuentasPantalla(repo) { nav.popBackStack() }
                }

                composable(Rutas.CATEGORIAS) {
                    CategoriasPantalla(repo) { nav.popBackStack() }
                }

                composable(Rutas.COMPROMISOS) {
                    CompromisosPantalla(
                        repo = repo,
                        alPagar = { id -> nav.navigate(Rutas.capturaDeCompromiso(id)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.CALIDAD) {
                    CalidadPantalla(
                        revisaCalidad = revisaCalidad,
                        reparaDatos = reparaDatos,
                        alRevisarHallazgo = { clave -> nav.navigate(Rutas.revision(clave)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(
                    route = Rutas.REVISION,
                    arguments = listOf(navArgument("clave") { type = NavType.StringType })
                ) { destino ->
                    RevisionPantalla(
                        repo = repo,
                        revisaCalidad = revisaCalidad,
                        clave = destino.arguments?.getString("clave").orEmpty(),
                        alAbrirMovimiento = { id -> nav.navigate(Rutas.captura(id)) },
                        alCerrar = { nav.popBackStack() }
                    )
                }

                composable(Rutas.AJUSTES) {
                    AjustesPantalla(
                        ajustes = ajustes,
                        repo = repo,
                        alAbrirCuentas = { nav.navigate(Rutas.CUENTAS) },
                        alAbrirCategorias = { nav.navigate(Rutas.CATEGORIAS) },
                        alAbrirCompromisos = { nav.navigate(Rutas.COMPROMISOS) },
                        alAbrirArchivo = { nav.navigate(Rutas.ARCHIVO) },
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
                        comprobador = contenedor.comprobadorActualizaciones,
                        ajustes = ajustes,
                        instalada = contenedor.version,
                        // Sin launchSingleTop se apilarian Acerca de y Tutoriales una y
                        // otra vez al ir y venir entre las dos.
                        alAbrirTutoriales = {
                            nav.navigate(Rutas.TUTORIALES) { launchSingleTop = true }
                        },
                        alCerrar = { nav.popBackStack() }
                    )
                }
            }

            /**
             * Los avisos de respaldo y de version nueva apuntan a Archivo, que
             * ya no es pestaña. Se abre encima del tablero en vez de arrancar
             * ahi: asi su boton de volver tiene a donde volver.
             *
             * Va despues del NavHost porque es el quien monta el grafo, y una
             * sola vez por intent: la actividad se vuelve a crear al girar el
             * telefono y relee el mismo extra, asi que sin la marca el aviso
             * reabriria Archivo cada vez que alguien rota la pantalla.
             */
            var avisoAtendido by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(rutaInicial) {
                if (!avisoAtendido && rutaInicial == Rutas.ARCHIVO) {
                    avisoAtendido = true
                    nav.navigate(Rutas.ARCHIVO)
                }
            }
        }
    }
}
