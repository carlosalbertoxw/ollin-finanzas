package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.excel.XlsxLector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * El lector no puede confiar en que el parser de la plataforma sepa prohibir el
 * DOCTYPE: en Android no sabe, porque su SAXParserFactory esta hecha sobre Expat
 * y solo reconoce las banderas de namespaces.
 *
 * Estas pruebas corren en la JVM, donde el parser si las reconoce, asi que no
 * pueden demostrar el comportamiento en el telefono. Lo que si fijan es que el
 * rechazo del DOCTYPE **no dependa** de esas banderas: se hace leyendo el
 * prologo a mano, y eso se comporta igual en las dos plataformas.
 */
class XlsxLectorSeguridadTest {

    /** Libro minimo pero valido, con una hoja y una celda de texto. */
    private fun libro(prologoDeHoja: String = ""): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            fun parte(ruta: String, contenido: String) {
                zip.putNextEntry(ZipEntry(ruta))
                zip.write(contenido.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            parte(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <workbook><sheets><sheet name="Registros" sheetId="1" r:id="rId1"/></sheets></workbook>"""
            )
            parte(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>"""
            )
            parte(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>$prologoDeHoja
                   <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Hola</t></is></c></row></sheetData></worksheet>"""
            )
        }
        return salida.toByteArray()
    }

    @Test
    fun `un libro normal se lee`() {
        val leido = XlsxLector.lee(ByteArrayInputStream(libro()))

        assertEquals(listOf("Registros"), leido.hojas.map { it.nombre })
        assertEquals("Hola", leido.hoja("Registros")!!.filas[0][0].comoTexto())
    }

    /**
     * La bomba de entidades: sin DOCTYPE no hay entidades que expandir, y por eso
     * el rechazo va antes de que el parser toque el archivo.
     */
    @Test
    fun `un libro con DOCTYPE se rechaza`() {
        val conBomba = libro(
            """<!DOCTYPE foo [<!ENTITY a "AAAAAAAAAA"><!ENTITY b "&a;&a;&a;&a;&a;">]>"""
        )

        try {
            XlsxLector.lee(ByteArrayInputStream(conBomba))
            fail("Un libro con DOCTYPE no debe leerse")
        } catch (e: XlsxLector.ArchivoInvalido) {
            assertTrue(
                "El mensaje debe decirle a la persona que hacer, no citar clases internas: ${e.message}",
                e.message!!.contains("DOCTYPE") && e.message!!.contains("hoja de calculo")
            )
        }
    }

    /** Un comentario en el prologo es legal y no debe confundirse con un DOCTYPE. */
    @Test
    fun `un comentario antes de la raiz no estorba`() {
        val leido = XlsxLector.lee(
            ByteArrayInputStream(libro("<!-- generado por otra suite, menciona DOCTYPE -->"))
        )

        assertEquals("Hola", leido.hoja("Registros")!!.filas[0][0].comoTexto())
    }

    /** `<!doctype` en minusculas es igual de valido para XML, y hay que atajarlo. */
    @Test
    fun `el DOCTYPE se detecta sin importar mayusculas`() {
        try {
            XlsxLector.lee(ByteArrayInputStream(libro("<!doctype foo>")))
            fail("Un libro con doctype en minusculas tampoco debe leerse")
        } catch (e: XlsxLector.ArchivoInvalido) {
            assertTrue(e.message!!.contains("DOCTYPE"))
        }
    }
}
