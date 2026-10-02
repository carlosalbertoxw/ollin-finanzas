package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.notify.AvisoDeRespaldo
import com.carlosalbertoxw.ollin.finanzas.data.notify.Respaldos
import com.carlosalbertoxw.ollin.finanzas.data.prefs.Ajustes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso del tablero: cuando sale, cuando se va y cuanto dura quitarlo.
 * Sin Android: el reloj se inyecta y se empuja a mano.
 */
class AvisoDeRespaldoTest {

    private val dia = 24L * 60 * 60 * 1000
    private val ancla = 1_000_000_000_000L

    private fun ajustes(
        recuerda: Boolean = true,
        ultimo: Long = 0L
    ) = Ajustes(recuerdaRespaldo = recuerda, ultimoRespaldo = ultimo, anclaDeRespaldo = ancla)

    // ------------------------------------------------------ si toca enseñarlo

    @Test
    fun `sale cuando la notificacion tambien saldria`() {
        val ahora = ancla + Respaldos.CADA_MS

        val texto = AvisoDeRespaldo.texto(ajustes(), descartado = false, ahora = ahora)

        assertEquals(Respaldos.textoDelAviso(0L, ancla, ahora), texto)
    }

    @Test
    fun `no sale antes de la semana`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(), descartado = false, ahora = ancla + 6 * dia))
    }

    @Test
    fun `exportar lo quita sin tocar nada mas`() {
        val ahora = ancla + 30 * dia

        assertNotNull(AvisoDeRespaldo.texto(ajustes(), false, ahora))
        assertNull(
            "Con un respaldo de hoy ya no toca",
            AvisoDeRespaldo.texto(ajustes(ultimo = ahora), false, ahora)
        )
    }

    @Test
    fun `apagar el recordatorio en Ajustes tambien lo apaga aqui`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(recuerda = false), false, ancla + 30 * dia))
    }

    @Test
    fun `quitado no sale aunque toque`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(), descartado = true, ahora = ancla + 30 * dia))
    }

    // ------------------------------------------------- cuanto dura quitarlo

    private var reloj = 5_000_000L
    private fun aviso() = AvisoDeRespaldo(reloj = { reloj })

    @Test
    fun `quitarlo dura mientras la app sigue abierta`() {
        val aviso = aviso()

        aviso.descarta()

        assertTrue(aviso.descartado.value)
    }

    /** El selector de archivos manda la app al fondo: volver de ahi no es abrirla. */
    @Test
    fun `salir un momento y volver no lo trae de regreso`() {
        val aviso = aviso()
        aviso.descarta()

        aviso.alIrAlFondo()
        reloj += AvisoDeRespaldo.AUSENCIA_MILLIS - 1
        aviso.alVolverAlFrente()

        assertTrue(aviso.descartado.value)
    }

    @Test
    fun `abrir la app otra vez lo vuelve a enseñar`() {
        val aviso = aviso()
        aviso.descarta()

        aviso.alIrAlFondo()
        reloj += AvisoDeRespaldo.AUSENCIA_MILLIS
        aviso.alVolverAlFrente()

        assertFalse("Ahora no no es nunca", aviso.descartado.value)
    }

    @Test
    fun `una app recien arrancada lo enseña`() {
        assertFalse(aviso().descartado.value)
    }
}
