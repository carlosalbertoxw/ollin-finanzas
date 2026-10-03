pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OllinFinanzas"
include(":app")

/**
 * Versiones minimas de bibliotecas que el build arrastra y que tienen fallos
 * conocidos (alertas de Dependabot).
 *
 * Ninguna viaja en el APK: las traen AGP, su Lint y Robolectric, y corren al
 * compilar o al probar. Aun asi corren en la maquina que firma la release, y
 * Bouncy Castle es justo lo que lee el almacen de claves.
 *
 * Va aqui y no en el build raiz porque tiene que alcanzar a *todas* las
 * configuraciones: el classpath de los plugins y tambien las que AGP crea por
 * su cuenta, como la de Lint (`androidLintTool`). Una restriccion en el
 * `buildscript` del raiz solo cubria la primera, y la de Lint seguia trayendo
 * Bouncy Castle 1.80.2.
 *
 * Solo sube versiones, nunca baja: si AGP trae algo mas nuevo, se respeta. Los
 * tres artefactos de Bouncy Castle van juntos porque mezclar versiones rompe en
 * tiempo de ejecucion. Cuando AGP traiga por si solo versiones iguales o
 * mayores, esto sobra y se quita.
 */
val versionesMinimas = mapOf(
    "org.bouncycastle:bcprov-jdk18on" to "1.86",
    "org.bouncycastle:bcpkix-jdk18on" to "1.86",
    "org.bouncycastle:bcutil-jdk18on" to "1.86",
    "org.apache.commons:commons-lang3" to "3.21.0",
    "org.apache.httpcomponents:httpclient" to "4.5.14",
    "org.bitbucket.b_c:jose4j" to "0.9.7",
    "org.jdom:jdom2" to "2.0.6.1"
)

fun ResolutionStrategy.exigeVersionesMinimas() = eachDependency {
    val minima = versionesMinimas["${requested.group}:${requested.name}"] ?: return@eachDependency
    val pedida = requested.version ?: return@eachDependency
    if (comparaVersiones(pedida, minima) < 0) {
        useVersion(minima)
        because("La $pedida tiene vulnerabilidades conocidas")
    }
}

/** Compara por numeros: 2.0.6 < 2.0.6.1 < 2.0.10. Lo que no es numero cuenta como 0. */
fun comparaVersiones(a: String, b: String): Int {
    val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diferencia = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
        if (diferencia != 0) return diferencia
    }
    return 0
}

gradle.beforeProject {
    buildscript.configurations.configureEach { resolutionStrategy.exigeVersionesMinimas() }
    configurations.configureEach { resolutionStrategy.exigeVersionesMinimas() }
}
