package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.excel.Ooxml
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
    private fun libro(
        prologoDeHoja: String = "",
        filas: String = """<row r="1"><c r="A1" t="inlineStr"><is><t>Hola</t></is></c></row>"""
    ): ByteArray {
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
                   <worksheet><sheetData>$filas</sheetData></worksheet>"""
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

    /**
     * La zip bomb: unos KB comprimidos que se expanden a mas que el limite
     * dentro de **una sola** parte. Antes se leia la parte entera y se media
     * despues; con una bomba de gigas, el `OutOfMemoryError` llegaba primero y
     * cerraba la app. Tiene que salir como archivo invalido, con mensaje legible.
     */
    @Test
    fun `una parte que se expande por encima del limite se rechaza`() {
        val bomba = ByteArrayOutputStream()
        ZipOutputStream(bomba).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val ceros = ByteArray(1024 * 1024)
            repeat((XlsxLector.LIMITE_BYTES / ceros.size).toInt() + 1) { zip.write(ceros) }
            zip.closeEntry()
        }
        assertTrue(
            "La bomba tiene que caber en poco: es lo que la hace peligrosa",
            bomba.size() < 1024 * 1024
        )

        try {
            XlsxLector.lee(ByteArrayInputStream(bomba.toByteArray()))
            fail("Una parte mas grande que el limite no debe leerse")
        } catch (e: XlsxLector.ArchivoInvalido) {
            assertTrue(e.message!!.contains("demasiado grande"))
        }
    }

    private fun rechaza(libro: ByteArray, motivo: String): XlsxLector.ArchivoInvalido {
        try {
            XlsxLector.lee(ByteArrayInputStream(libro))
        } catch (e: XlsxLector.ArchivoInvalido) {
            return e
        }
        fail(motivo)
        error("inalcanzable")
    }

    /**
     * Una fila con un numero enorme son unos pocos bytes, y antes obligaba a
     * rellenar dos mil millones de filas vacias: `OutOfMemoryError` y la app
     * cerrada en vez de un archivo rechazado.
     */
    @Test
    fun `una fila mas alla del limite de Excel se rechaza`() {
        val e = rechaza(
            libro(filas = """<row r="2000000000"><c r="A2000000000"><v>1</v></c></row>"""),
            "Una fila fuera de la hoja no debe leerse"
        )
        assertTrue(e.message!!.contains("fuera de los limites"))
    }

    /** XFD es la ultima columna de Excel; una mas alla ya no es una hoja real. */
    @Test
    fun `una columna mas alla de XFD se rechaza`() {
        val e = rechaza(
            libro(filas = """<row r="1"><c r="XFE1"><v>1</v></c></row>"""),
            "Una columna fuera de la hoja no debe leerse"
        )
        assertTrue(e.message!!.contains("fuera de los limites"))
    }

    /** Con letras de mas, el indice desbordaba el Int y podia salir negativo. */
    @Test
    fun `una referencia con letras de mas se rechaza en vez de desbordar`() {
        rechaza(
            libro(filas = """<row r="1"><c r="AAAAAAAAAAAA1"><v>1</v></c></row>"""),
            "Una referencia desbordada no debe leerse"
        )
        assertEquals(Int.MAX_VALUE, Ooxml.indiceColumna("ZZZZZZZZZZZZ"))
    }

    /**
     * Cada fila valida por si sola, pero todas juntas piden mas celdas de las
     * que caben: doscientas filas con una celda en XFD son 3,2 millones.
     */
    @Test
    fun `muchas filas anchas agotan el presupuesto de celdas`() {
        val anchas = (1..200).joinToString("") {
            """<row r="$it"><c r="XFD$it"><v>1</v></c></row>"""
        }
        val e = rechaza(libro(filas = anchas), "Un libro que pasa del presupuesto no debe leerse")
        assertTrue(e.message!!.contains("demasiado grande"))
    }

    /** Los huecos legitimos siguen leyendose: la fila 5 queda en su sitio. */
    @Test
    fun `una fila salteada dentro del limite se rellena`() {
        val leido = XlsxLector.lee(
            ByteArrayInputStream(
                libro(
                    filas = """<row r="5"><c r="C5" t="inlineStr"><is><t>Lejos</t></is></c></row>"""
                )
            )
        )
        val filas = leido.hoja("Registros")!!.filas
        assertEquals(5, filas.size)
        assertEquals("Lejos", filas[4][2].comoTexto())
        assertTrue(filas[4][0].estaVacia)
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
