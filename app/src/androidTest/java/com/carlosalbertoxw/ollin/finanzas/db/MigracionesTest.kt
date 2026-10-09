package com.carlosalbertoxw.ollin.finanzas.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.finanzas.data.db.MIGRACIONES
import com.carlosalbertoxw.ollin.finanzas.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.finanzas.data.db.VERSION_BASE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Corre las migraciones de verdad contra los esquemas exportados.
 *
 * `EsquemaDeBaseTest` comprueba en la JVM que la cadena no tenga huecos; esta
 * comprueba que el SQL funcione. Son cosas distintas: una migracion puede
 * existir, estar bien encadenada y aun asi dejar una columna con el tipo
 * equivocado o sin su indice, y entonces Room se niega a abrir la base **en el
 * telefono de quien actualizo**, que es el peor sitio para enterarse.
 *
 * MigrationTestHelper compara la base resultante contra el `N.json` que KSP
 * exporto de las entidades, asi que valida el resultado, no la intencion.
 *
 * Necesita un telefono o un emulador: la validacion usa el SQLite del sistema.
 * Va sin cifrar a proposito --el helper abre con el SQLite normal, no con
 * SQLCipher--, y da igual: el cifrado envuelve el archivo entero y las
 * migraciones ven el mismo esquema con llave o sin ella.
 *
 * Existe antes que la primera migracion para que esa no llegue sin prueba. Con
 * el esquema todavia en 1 comprueba lo unico que hay que comprobar: que
 * `1.json` describe una base que Room acepta como suya.
 *
 * **Al agregar una migracion**, se agrega aqui una prueba propia que ademas
 * escriba un renglon antes de migrar y lo vuelva a leer despues. La cadena de
 * abajo demuestra que el esquema llega entero; solo un dato real demuestra que
 * el libro sobrevive al viaje.
 */
@RunWith(AndroidJUnit4::class)
class MigracionesTest {

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OllinDatabase::class.java
    )

    /**
     * De la primera version a la actual, de un tiron: el camino de quien
     * instalo Ollin el primer dia y actualiza hoy, saltandose las intermedias.
     */
    @Test
    fun laBaseMigraDeLaPrimeraVersionALaActual() {
        ayudante.createDatabase(NOMBRE, 1).close()

        ayudante.runMigrationsAndValidate(
            NOMBRE,
            VERSION_BASE,
            /* validateDroppedTables = */
            true,
            *MIGRACIONES
        ).close()
    }

    /**
     * Y version por version, que es el camino de quien actualiza siempre.
     *
     * No es el mismo recorrido: encadenar los pasos de uno en uno puede fallar
     * donde el salto largo no falla, cuando una migracion deshace algo que la
     * siguiente da por hecho.
     */
    @Test
    fun cadaSaltoIntermedioDejaLaBaseValida() {
        for (version in 1 until VERSION_BASE) {
            ayudante.createDatabase(NOMBRE, version).close()
            ayudante.runMigrationsAndValidate(NOMBRE, version + 1, true, *MIGRACIONES).close()
        }
    }

    private companion object {
        const val NOMBRE = "migraciones-prueba.db"
    }
}
