package com.carlosalbertoxw.ollin.finanzas.data.db

import androidx.room.migration.Migration

/**
 * Las migraciones del esquema, en orden y sin huecos.
 *
 * Esta vacio porque la base sigue en su version inicial. En cuanto la app este
 * en el telefono de alguien mas, cambiar una entidad deja de ser gratis: al
 * otro lado hay un libro de finanzas que hay que conservar, y Room se niega a
 * abrir una base cuyo esquema no reconoce.
 *
 * El procedimiento completo esta en docs/modelo-de-datos.md. En corto:
 *
 * 1. Cambia las entidades.
 * 2. Sube [VERSION_BASE].
 * 3. Compila: KSP escribe `app/schemas/<N>.json`, que se versiona en git.
 * 4. Agrega aqui la migracion de N-1 a N, mirando el json nuevo contra el
 *    anterior para escribir el SQL exacto.
 *
 * ```kotlin
 * private val DE_1_A_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE compromiso ADD COLUMN diaDePago INTEGER")
 *     }
 * }
 * ```
 *
 * [EsquemaDeBaseTest] revisa que la cadena llegue de 1 a [VERSION_BASE] sin
 * saltos y que cada version tenga su json exportado. Lo que no puede revisar
 * desde la JVM es si el SQL de la migracion es correcto: eso pide una prueba
 * instrumentada con `MigrationTestHelper`, tambien descrita en la doc.
 */
val MIGRACIONES: Array<Migration> = emptyArray()
