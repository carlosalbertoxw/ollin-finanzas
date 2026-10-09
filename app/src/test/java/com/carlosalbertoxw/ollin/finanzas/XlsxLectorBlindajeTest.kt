package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.excel.Celda
import com.carlosalbertoxw.ollin.finanzas.data.excel.Hoja
import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxEscritor
import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxLector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

/**
 * El blindaje del parser no puede costar una importacion.
 *
 * Cada implementacion de SAXParserFactory soporta un juego distinto de
 * banderas, y la de Android no es la de la JVM: `setXIncludeAware` no esta
 * implementada alli y la clase base lanza UnsupportedOperationException. Una
 * version de este codigo pedia esa bandera sin red y tumbaba **toda**
 * importacion en el telefono, mientras las pruebas seguian en verde porque en
 * la JVM la sirve Xerces, que si la soporta.
 *
 * Estas pruebas cierran ese hueco sustituyendo la fabrica por una que se
 * comporta como la de Android, via la propiedad de sistema que
 * `SAXParserFactory.newInstance()` consulta.
 */
class XlsxLectorBlindajeTest {

    private val propiedad = "javax.xml.parsers.SAXParserFactory"

    @After
    fun restaura() {
        System.clearProperty(propiedad)
    }

    private fun libro(): ByteArray {
        val salida = ByteArrayOutputStream()
        XlsxEscritor(
            listOf(
                Hoja(
                    nombre = "Movimientos",
                    filas = listOf(
                        listOf(Celda.Texto("Fecha"), Celda.Texto("Concepto")),
                        listOf(Celda.Texto("2026-08-10"), Celda.Texto("Renta"))
                    )
                )
            )
        ).escribeEn(salida)
        return salida.toByteArray()
    }

    @Test
    fun `se lee aunque la fabrica no implemente setXIncludeAware`() {
        System.setProperty(propiedad, FabricaSinXInclude::class.java.name)

        val leido = XlsxLector.lee(libro().inputStream())

        assertEquals(1, leido.hojas.size)
        assertEquals("Renta", leido.hojas[0].filas[1][1].comoTexto())
    }

    @Test
    fun `se lee aunque la fabrica rechace todas las banderas de seguridad`() {
        System.setProperty(propiedad, FabricaSinBanderas::class.java.name)

        val leido = XlsxLector.lee(libro().inputStream())

        assertEquals("Renta", leido.hojas[0].filas[1][1].comoTexto())
    }
}

/** Delega en la de verdad y solo se niega a lo que Android tampoco sabe hacer. */
abstract class FabricaDelegante : SAXParserFactory() {

    protected val real: SAXParserFactory = SAXParserFactory.newInstance(
        "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
        null
    )

    override fun newSAXParser(): SAXParser = real.newSAXParser()
    override fun setFeature(nombre: String, valor: Boolean) = real.setFeature(nombre, valor)
    override fun getFeature(nombre: String): Boolean = real.getFeature(nombre)
    override fun setNamespaceAware(valor: Boolean) {
        real.isNamespaceAware = valor
    }
    override fun isNamespaceAware(): Boolean = real.isNamespaceAware
    override fun setValidating(valor: Boolean) {
        real.isValidating = valor
    }
    override fun isValidating(): Boolean = real.isValidating
}

/** Como Android: la clase base lanza al pedirle XInclude. */
class FabricaSinXInclude : FabricaDelegante() {
    override fun setXIncludeAware(valor: Boolean): Unit =
        throw UnsupportedOperationException("This parser does not support XInclude")
}

/** El caso extremo: ni XInclude ni ninguna de las banderas de seguridad. */
class FabricaSinBanderas : FabricaDelegante() {
    override fun setXIncludeAware(valor: Boolean): Unit =
        throw UnsupportedOperationException("This parser does not support XInclude")

    override fun setFeature(nombre: String, valor: Boolean): Unit =
        throw org.xml.sax.SAXNotRecognizedException(nombre)
}
