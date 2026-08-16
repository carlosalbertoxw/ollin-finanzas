package com.carlosalbertoxw.ollin.finanzas

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.ollin.finanzas.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.finanzas.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaCifra
import com.carlosalbertoxw.ollin.finanzas.ui.components.TarjetaValor
import com.carlosalbertoxw.ollin.finanzas.ui.components.TextoDinero
import com.carlosalbertoxw.ollin.finanzas.ui.theme.TemaOllin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Componentes montados solos, sin actividad ni base de datos.
 *
 * Son las pruebas de interfaz mas baratas que tiene el proyecto: no dependen de
 * que haya datos sembrados ni del estado en que quedo el telefono, asi que si
 * una falla es porque el componente cambio, no porque el ambiente se movio.
 */
@RunWith(AndroidJUnit4::class)
class ComponentesComunesTest {

    @get:Rule
    val compose = createComposeRule()

    /** Los componentes leen LocalColoresOllin, que solo existe dentro del tema. */
    private fun monta(contenido: @Composable () -> Unit) {
        compose.setContent { TemaOllin(oscuro = false) { contenido() } }
    }

    @Test
    fun textoDineroMuestraElImporteConMonedaYMillares() {
        monta { TextoDinero(centavos = 123456) }
        compose.onNodeWithText("\$1,234.56").assertIsDisplayed()
    }

    @Test
    fun textoDineroCompactoAbreviaElMillon() {
        monta { TextoDinero(centavos = 120_000_000, compacto = true) }
        compose.onNodeWithText("\$1.2M").assertIsDisplayed()
    }

    @Test
    fun textoDineroConservaElSignoNegativo() {
        monta { TextoDinero(centavos = -59980) }
        compose.onNodeWithText("-\$599.80").assertIsDisplayed()
    }

    @Test
    fun tarjetaCifraMuestraEtiquetaImporteYNota() {
        monta {
            TarjetaCifra(
                etiqueta = "Patrimonio neto",
                centavos = 1_250_000,
                nota = "al cierre de enero"
            )
        }
        compose.onNodeWithText("Patrimonio neto").assertIsDisplayed()
        compose.onNodeWithText("\$12,500.00").assertIsDisplayed()
        compose.onNodeWithText("al cierre de enero").assertIsDisplayed()
    }

    @Test
    fun tarjetaValorMuestraUnValorLibre() {
        monta {
            TarjetaValor(
                etiqueta = "Tasa de ahorro",
                valor = "34%",
                nota = "sobre consumo real"
            )
        }
        compose.onNodeWithText("Tasa de ahorro").assertIsDisplayed()
        compose.onNodeWithText("34%").assertIsDisplayed()
        compose.onNodeWithText("sobre consumo real").assertIsDisplayed()
    }

    @Test
    fun estadoVacioMuestraTituloDetalleYAccion() {
        monta {
            EstadoVacio(
                icono = Icons.Filled.ReceiptLong,
                titulo = "Todavia no hay movimientos",
                detalle = "Captura el primero o importa tu libro de Excel.",
                accion = { Text("Importar") }
            )
        }
        compose.onNodeWithText("Todavia no hay movimientos").assertIsDisplayed()
        compose.onNodeWithText("Captura el primero o importa tu libro de Excel.").assertIsDisplayed()
        compose.onNodeWithText("Importar").assertIsDisplayed()
    }

    @Test
    fun seccionTituloMuestraSuTextoYSuAccion() {
        monta { SeccionTitulo("Cuentas", accion = { Text("Ver todas") }) }
        compose.onNodeWithText("Cuentas").assertIsDisplayed()
        compose.onNodeWithText("Ver todas").assertIsDisplayed()
    }
}
