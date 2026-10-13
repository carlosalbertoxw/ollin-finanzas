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
