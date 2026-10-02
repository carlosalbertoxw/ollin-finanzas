/**
 * Bouncy Castle, mas nuevo que el que trae AGP.
 *
 * AGP 9.4.1 llega con la 1.80.2, que tiene dos fallos criticos y uno alto en su
 * parser de ASN.1 y en la validacion de certificados (alertas de Dependabot,
 * corregidos en la 1.85). Solo corre al compilar, no viaja en el APK, pero es
 * justo lo que lee el almacen de claves al firmar la release.
 *
 * Van los tres artefactos juntos y en la misma version: mezclar un bcpkix viejo
 * con un bcprov nuevo rompe en tiempo de ejecucion. Cuando AGP traiga por si
 * solo una version igual o mayor, este bloque sobra y se quita.
 */
buildscript {
    dependencies {
        constraints {
            listOf("bcprov", "bcpkix", "bcutil").forEach { artefacto ->
                classpath("org.bouncycastle:$artefacto-jdk18on:1.86") {
                    because("La 1.80.2 que trae AGP tiene vulnerabilidades corregidas en la 1.85")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
