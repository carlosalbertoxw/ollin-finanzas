package mx.ollin.finanzas.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Crea un ViewModel pasandole el contenedor a mano. Evita tener que declarar
 * una Factory por pantalla, que es puro ceremonial cuando no hay inyeccion.
 */
@Composable
inline fun <reified VM : ViewModel> recuerdaVm(
    clave: String? = null,
    crossinline crear: () -> VM
): VM = viewModel(
    key = clave,
    factory = viewModelFactory { initializer { crear() } }
)
