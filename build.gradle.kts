/*
 * Versiones corregidas de lo que los plugins arrastran al classpath del build.
 *
 * CycloneDX trae jackson 2.22.2 y Kover trae freemarker 2.3.32, las dos con
 * vulnerabilidades conocidas (Dependabot las marca contra settings.gradle.kts).
 * No viajan en el APK, pero corren en la maquina que compila y firma las
 * releases. Dependabot no puede subirlas porque no son dependencias directas,
 * asi que se fijan aqui; cuando los plugins traigan unas iguales o mas nuevas,
 * esto se puede quitar.
 */
buildscript {
    dependencies {
        classpath(platform(libs.jackson.bom))
        constraints {
            classpath(libs.freemarker)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.licensee) apply false
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// El estilo del codigo, comprobado y no solo escrito.
//
// El .editorconfig ya declaraba el estilo oficial de Kotlin, pero nada lo
// hacia cumplir: cada editor lo aplicaba a su manera, o no lo aplicaba. Aqui
// lo aplica ktlint, que lee ese mismo .editorconfig, y `spotlessCheck` corre en
// pruebas.yml junto con las pruebas y Lint.
//
//     ./gradlew spotlessApply   # reformatea
//     ./gradlew spotlessCheck   # solo comprueba, como en CI
//
// Va con Spotless y no con el plugin de ktlint para Gradle porque Spotless no
// depende de los source sets del plugin de Kotlin, que desde AGP 9 ya no se
// aplica.
spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
