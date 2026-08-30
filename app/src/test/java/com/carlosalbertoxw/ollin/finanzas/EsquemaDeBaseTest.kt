package com.carlosalbertoxw.ollin.finanzas

import com.carlosalbertoxw.ollin.finanzas.data.db.MIGRACIONES
import com.carlosalbertoxw.ollin.finanzas.data.db.VERSION_BASE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La guardia de la base: que cada version del esquema tenga su json exportado
 * y que la cadena de migraciones llegue de la 1 a la vigente sin saltos.
 *
 * Es barata y corre en la JVM, asi que atrapa el olvido tipico —subir la
 * version y no escribir la migracion— en el mismo commit que lo comete, y no
 * en el telefono de alguien que ya tenia datos dentro.
 *
 * Lo que no puede decir es si el SQL de una migracion hace lo correcto: eso
 * pide `MigrationTestHelper` con dispositivo. Ver docs/modelo-de-datos.md.
 */
class EsquemaDeBaseTest {

    private val carpeta: File by lazy {
        val ruta = "schemas/com.carlosalbertoxw.ollin.finanzas.data.db.OllinDatabase"
        // Gradle corre las pruebas desde el modulo; desde la raiz del
        // repositorio la ruta lleva el prefijo del modulo.
        listOf(File(ruta), File("app/$ruta")).firstOrNull { it.isDirectory }
            ?: throw AssertionError("No encuentro los esquemas exportados. ¿Se borro app/schemas/?")
    }

    private fun esquema(version: Int) = File(carpeta, "$version.json")

    @Test
    fun `la version vigente tiene su esquema exportado`() {
        val archivo = esquema(VERSION_BASE)

        assertTrue(
            "Falta ${archivo.name}. Compila una vez para que KSP lo escriba y agregalo a git.",
            archivo.isFile
        )
    }

    @Test
    fun `el esquema exportado declara la misma version que la base`() {
        // A mano y sin parsear el json entero: solo hace falta un numero, y
        // "formatVersion" no puede confundirse con esto porque la comilla de
        // apertura forma parte de lo que se busca.
        val declarada = esquema(VERSION_BASE).readText()
            .substringAfter("\"version\":", "")
            .trimStart()
            .takeWhile(Char::isDigit)
            .toIntOrNull()

        assertEquals(
            "El json exportado no coincide con VERSION_BASE. Recompila para regenerarlo.",
            VERSION_BASE,
            declarada
        )
    }

    @Test
    fun `ninguna version del esquema se quedo sin exportar`() {
        val faltantes = (1..VERSION_BASE).filterNot { esquema(it).isFile }

        assertTrue(
            "Los esquemas de estas versiones no estan en git: $faltantes. Sin ellos no " +
                "hay contra que escribir la migracion siguiente.",
            faltantes.isEmpty()
        )
    }

    @Test
    fun `la cadena de migraciones llega hasta la version vigente sin saltos`() {
        val pasos = MIGRACIONES.map { it.startVersion to it.endVersion }.toSet()
        val faltantes = (2..VERSION_BASE).map { it - 1 to it }.filterNot { it in pasos }

        assertTrue(
            "Falta la migracion de estos pasos: $faltantes. Room se niega a abrir una base " +
                "cuya version no sabe alcanzar, y la app no arranca en el telefono que ya " +
                "tenia datos.",
            faltantes.isEmpty()
        )
    }

    @Test
    fun `ninguna migracion apunta fuera del rango de versiones`() {
        val fuera = MIGRACIONES.filter {
            it.startVersion < 1 || it.endVersion > VERSION_BASE || it.startVersion >= it.endVersion
        }

        assertTrue(
            "Estas migraciones no van de N a N+1 dentro del rango 1..$VERSION_BASE: " +
                fuera.map { it.startVersion to it.endVersion },
            fuera.isEmpty()
        )
    }
}
