package mx.ollin.finanzas.data.excel

import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escribe un .xlsx completo sin dependencias externas.
 *
 * Por que no Apache POI: en Android pesa del orden de 15 MB, mete decenas de
 * miles de metodos y obliga a desugaring. Aqui el formato producido esta bajo
 * nuestro control, asi que un escritor propio de ~400 lineas es mas pequeño,
 * mas rapido de arrancar y no puede sorprendernos.
 *
 * Decision de diseño importante: las hojas de analisis salen con formulas vivas
 * (SUMIFS y compañia) en vez de tablas dinamicas. Razones:
 *  - Una dinamica exige refresco manual y hasta entonces muestra numeros viejos.
 *    Con `fullCalcOnLoad` las formulas se recalculan solas al abrir.
 *  - Las formulas se comportan igual en Excel, WPS Office, LibreOffice y Sheets.
 *  - Permiten exportar solo algunas pestañas sin dejar cachés huerfanos.
 */
class XlsxEscritor(private val hojas: List<Hoja>) {

    init {
        require(hojas.isNotEmpty()) { "Un libro necesita al menos una hoja" }
        val nombres = hojas.map { it.nombre.lowercase() }
        require(nombres.size == nombres.toSet().size) { "Hay nombres de hoja repetidos" }
    }

    private val cadenas = LinkedHashMap<String, Int>()

    /** Tablas a emitir: hoja (indice base 0) -> definicion. */
    private val tablas: List<Pair<Int, TablaExcel>> =
        hojas.mapIndexedNotNull { i, h -> h.tabla?.let { i to it } }

    fun escribeEn(salida: OutputStream) {
        // Las cadenas se indexan antes de serializar cualquier hoja, porque
        // sharedStrings.xml debe conocer el total al escribir su cabecera.
        val cuerposHoja = hojas.map { hojaXml(it) }

        ZipOutputStream(salida.buffered()).use { zip ->
            zip.setLevel(6)
            agrega(zip, "[Content_Types].xml", contentTypesXml())
            agrega(zip, "_rels/.rels", relsRaizXml())
            agrega(zip, "docProps/core.xml", coreXml())
            agrega(zip, "docProps/app.xml", appXml())
            agrega(zip, "xl/workbook.xml", workbookXml())
            agrega(zip, "xl/_rels/workbook.xml.rels", relsWorkbookXml())
            agrega(zip, "xl/styles.xml", estilosXml())
            agrega(zip, "xl/sharedStrings.xml", sharedStringsXml())

            cuerposHoja.forEachIndexed { i, cuerpo ->
                agrega(zip, "xl/worksheets/sheet${i + 1}.xml", cuerpo)
            }
            tablas.forEachIndexed { n, (indiceHoja, tabla) ->
                agrega(
                    zip,
                    "xl/worksheets/_rels/sheet${indiceHoja + 1}.xml.rels",
                    relsHojaXml(n + 1)
                )
                agrega(zip, "xl/tables/table${n + 1}.xml", tablaXml(n + 1, tabla))
            }
        }
    }

    private fun agrega(zip: ZipOutputStream, ruta: String, contenido: String) {
        zip.putNextEntry(ZipEntry(ruta))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun indiceCadena(texto: String): Int =
        cadenas.getOrPut(texto) { cadenas.size }

    // ------------------------------------------------------------- partes

    private fun contentTypesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        hojas.indices.forEach {
            append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        tablas.indices.forEach {
            append("""<Override PartName="/xl/tables/table${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"/>""")
        }
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        append("</Types>")
    }

    private fun relsRaizXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
            """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>""" +
            "</Relationships>"

    private fun coreXml(): String {
        val ahora = DateTimeFormatter.ISO_INSTANT.format(Instant.now().let {
            Instant.ofEpochSecond(it.epochSecond)
        })
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" """ +
            """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""" +
            "<dc:title>Finanzas</dc:title><dc:creator>Ollin</dc:creator>" +
            "<cp:lastModifiedBy>Ollin</cp:lastModifiedBy>" +
            """<dcterms:created xsi:type="dcterms:W3CDTF">$ahora</dcterms:created>""" +
            """<dcterms:modified xsi:type="dcterms:W3CDTF">$ahora</dcterms:modified>""" +
            "</cp:coreProperties>"
    }

    private fun appXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" """ +
            """xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">""" +
            "<Application>Ollin</Application><Company></Company>" +
            "</Properties>"

    private fun workbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<workbookPr date1904="false"/>""")
        append("""<bookViews><workbookView xWindow="0" yWindow="0" windowWidth="20000" windowHeight="12000"/></bookViews>""")
        append("<sheets>")
        hojas.forEachIndexed { i, hoja ->
            append("""<sheet name="${Ooxml.escapaXml(Ooxml.saneaNombreHoja(hoja.nombre))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets>")
        // Recalcular al abrir: es lo que evita que el archivo muestre numeros
        // viejos hasta que alguien refresque a mano.
        append("""<calcPr calcId="191029" fullCalcOnLoad="1"/>""")
        append("</workbook>")
    }

    private fun relsWorkbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        hojas.indices.forEach {
            append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
        }
        val base = hojas.size
        append("""<Relationship Id="rId${base + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId${base + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("</Relationships>")
    }

    private fun relsHojaXml(numeroTabla: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/table" Target="../tables/table$numeroTabla.xml"/>""" +
            "</Relationships>"

    /**
     * Los indices de cellXfs de aqui son los que expone [Estilo].
     * Cualquier cambio de orden rompe todos los estilos del libro.
     */
    private fun estilosXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        append("""<numFmts count="4">""")
        append("""<numFmt numFmtId="164" formatCode="yyyy\-mm\-dd"/>""")
        append("""<numFmt numFmtId="165" formatCode="&quot;$&quot;#,##0.00;[Red]\-&quot;$&quot;#,##0.00;&quot;$&quot;\-"/>""")
        append("""<numFmt numFmtId="166" formatCode="0.0%"/>""")
        append("""<numFmt numFmtId="167" formatCode="#,##0"/>""")
        append("</numFmts>")

        append("""<fonts count="6">""")
        append("""<font><sz val="11"/><color theme="1"/><name val="Arial"/><family val="2"/></font>""")
        append("""<font><b/><sz val="11"/><color theme="1"/><name val="Arial"/><family val="2"/></font>""")
        append("""<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/><family val="2"/></font>""")
        append("""<font><b/><sz val="14"/><color rgb="FF12181B"/><name val="Arial"/><family val="2"/></font>""")
        append("""<font><i/><sz val="10"/><color rgb="FF6B7280"/><name val="Arial"/><family val="2"/></font>""")
        append("""<font><sz val="11"/><color rgb="FFC4453F"/><name val="Arial"/><family val="2"/></font>""")
        append("</fonts>")

        append("""<fills count="5">""")
        append("""<fill><patternFill patternType="none"/></fill>""")
        append("""<fill><patternFill patternType="gray125"/></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FF12181B"/><bgColor indexed="64"/></patternFill></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFEDF3F0"/><bgColor indexed="64"/></patternFill></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFFBEDEC"/><bgColor indexed="64"/></patternFill></fill>""")
        append("</fills>")

        append("""<borders count="2">""")
        append("""<border><left/><right/><top/><bottom/><diagonal/></border>""")
        append("""<border><left/><right/><top/><bottom style="thin"><color rgb="FFB9C4C0"/></bottom><diagonal/></border>""")
        append("</borders>")

        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")

        append("""<cellXfs count="13">""")
        //  0 NORMAL
        append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""")
        //  1 FECHA
        append("""<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
        //  2 DINERO
        append("""<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
        //  3 PORCENTAJE
        append("""<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
        //  4 ENTERO
        append("""<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""")
        //  5 ENCABEZADO
        append("""<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>""")
        //  6 NEGRITA
        append("""<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""")
        //  7 TITULO
        append("""<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>""")
        //  8 DINERO_TOTAL
        append("""<xf numFmtId="165" fontId="1" fillId="3" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1"/>""")
        //  9 TENUE
        append("""<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>""")
        // 10 PORCENTAJE_TOTAL
        append("""<xf numFmtId="166" fontId="1" fillId="3" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1"/>""")
        // 11 DINERO_NEGATIVO
        append("""<xf numFmtId="165" fontId="5" fillId="4" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1"/>""")
        // 12 SUBTITULO
        append("""<xf numFmtId="0" fontId="1" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""")
        append("</cellXfs>")

        append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
        append("""<dxfs count="0"/>""")
        append("""<tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>""")
        append("</styleSheet>")
    }

    private fun sharedStringsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${cadenas.size}" uniqueCount="${cadenas.size}">""")
        cadenas.keys.forEach {
            append("""<si><t xml:space="preserve">${Ooxml.escapaXml(it)}</t></si>""")
        }
        append("</sst>")
    }

    private fun hojaXml(hoja: Hoja): String = buildString {
        val filas = hoja.totalFilas.coerceAtLeast(1)
        val columnas = hoja.totalColumnas.coerceAtLeast(1)
        val dimension = "A1:${Ooxml.referencia(filas, columnas)}"

        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<dimension ref="$dimension"/>""")

        append("<sheetViews><sheetView workbookViewId=\"0\"")
        if (hoja === hojas.first()) append(" tabSelected=\"1\"")
        append(">")
        if (hoja.congelarTrasFila > 0) {
            val primera = hoja.congelarTrasFila + 1
            append("""<pane ySplit="${hoja.congelarTrasFila}" topLeftCell="A$primera" activePane="bottomLeft" state="frozen"/>""")
            append("""<selection pane="bottomLeft" activeCell="A$primera" sqref="A$primera"/>""")
        }
        append("</sheetView></sheetViews>")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")

        if (hoja.anchos.isNotEmpty()) {
            append("<cols>")
            hoja.anchos.sortedBy { it.columna }.forEach {
                append("""<col min="${it.columna}" max="${it.columna}" width="${"%.2f".format(java.util.Locale.US, it.ancho)}" customWidth="1"/>""")
            }
            append("</cols>")
        }

        append("<sheetData>")
        hoja.filas.forEachIndexed { indice, fila ->
            val numeroFila = indice + 1
            if (fila.all { it is Celda.Vacia }) {
                append("""<row r="$numeroFila"/>""")
                return@forEachIndexed
            }
            append("""<row r="$numeroFila" spans="1:${fila.size}">""")
            fila.forEachIndexed { c, celda ->
                append(celdaXml(numeroFila, c + 1, celda))
            }
            append("</row>")
        }
        append("</sheetData>")

        // Una tabla trae su propio autofiltro; poner los dos hace que Excel
        // reclame el archivo como dañado.
        if (hoja.tabla == null && hoja.autoFiltro != null) {
            append("""<autoFilter ref="${hoja.autoFiltro}"/>""")
        }

        if (hoja.validaciones.isNotEmpty()) {
            append("""<dataValidations count="${hoja.validaciones.size}">""")
            hoja.validaciones.forEach {
                append("""<dataValidation type="list" allowBlank="1" showInputMessage="1" showErrorMessage="1" sqref="${it.rangoDestino}">""")
                append("<formula1>${Ooxml.escapaXml(it.origenFormula)}</formula1>")
                append("</dataValidation>")
            }
            append("</dataValidations>")
        }

        if (hoja.tabla != null) {
            append("""<tableParts count="1"><tablePart r:id="rId1"/></tableParts>""")
        }

        append("</worksheet>")
    }

    private fun celdaXml(fila: Int, columna: Int, celda: Celda): String {
        if (celda is Celda.Vacia) return ""
        val ref = Ooxml.referencia(fila, columna)
        val s = if (celda.estilo != 0) """ s="${celda.estilo}"""" else ""

        return when (celda) {
            is Celda.Vacia -> ""

            is Celda.Texto ->
                if (celda.valor.isEmpty()) """<c r="$ref"$s/>"""
                else """<c r="$ref"$s t="s"><v>${indiceCadena(celda.valor)}</v></c>"""

            is Celda.Numero ->
                """<c r="$ref"$s><v>${numero(celda.valor)}</v></c>"""

            is Celda.Fecha ->
                """<c r="$ref"$s><v>${Ooxml.aSerial(celda.valor)}</v></c>"""

            is Celda.Booleano ->
                """<c r="$ref"$s t="b"><v>${if (celda.valor) 1 else 0}</v></c>"""

            is Celda.Formula -> {
                // La formula se guarda sin el "=" inicial.
                val expr = Ooxml.escapaXml(celda.expresion.removePrefix("="))
                when {
                    celda.cacheTexto != null ->
                        """<c r="$ref"$s t="str"><f>$expr</f><v>${Ooxml.escapaXml(celda.cacheTexto)}</v></c>"""
                    celda.cache != null ->
                        """<c r="$ref"$s><f>$expr</f><v>${numero(celda.cache)}</v></c>"""
                    else ->
                        """<c r="$ref"$s><f>$expr</f></c>"""
                }
            }
        }
    }

    /** Sin notacion cientifica ni separadores de miles: Excel solo acepta el punto. */
    private fun numero(valor: Double): String {
        if (valor == valor.toLong().toDouble()) return valor.toLong().toString()
        return java.math.BigDecimal(valor)
            .setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun tablaXml(numero: Int, tabla: TablaExcel): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<table xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""id="$numero" name="${Ooxml.escapaXml(tabla.nombre)}" displayName="${Ooxml.escapaXml(tabla.nombre)}" """)
        append("""ref="${tabla.rango}" totalsRowShown="0">""")
        append("""<autoFilter ref="${tabla.rango}"/>""")
        append("""<tableColumns count="${tabla.encabezados.size}">""")
        tabla.encabezados.forEachIndexed { i, nombre ->
            append("""<tableColumn id="${i + 1}" name="${Ooxml.escapaXml(nombre)}"/>""")
        }
        append("</tableColumns>")
        append("""<tableStyleInfo name="TableStyleMedium2" showFirstColumn="0" showLastColumn="0" showRowStripes="1" showColumnStripes="0"/>""")
        append("</table>")
    }
}
