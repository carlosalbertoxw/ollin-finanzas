import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component
import java.util.Properties

plugins {
    // Sin `kotlin.android`: desde AGP 9 el plugin de Android compila Kotlin por
    // si mismo, y aplicar los dos es un error de configuracion.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.licensee)
    alias(libs.plugins.ktlint)
}

/**
 * Credenciales de firma.
 *
 * Se leen de `keystore.properties` en la raiz, que no se versiona; si no
 * existe, se caen a variables de entorno, que es lo que sirve en un servidor de
 * integracion. Ver `keystore.properties.example` y docs/publicacion.md.
 *
 * Nunca van escritas aqui: este archivo si viaja en el repositorio, y un
 * almacen filtrado permite publicar actualizaciones falsas de Ollin Finanzas
 * que Android instalaria sin protestar.
 *
 * Las variables llevan el nombre completo de la app y no un `OLLIN_` a secas:
 * Ollin Actividades se publica aparte y con su propio almacen, y en un servidor
 * compartido unos nombres genericos harian que cada app tomara la llave de la
 * otra sin avisar.
 */
val credenciales = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.isFile) archivo.inputStream().use(::load)
}

fun credencial(clave: String, variable: String): String? =
    (credenciales.getProperty(clave) ?: System.getenv(variable))?.takeIf { it.isNotBlank() }

val almacenDeClaves = credencial("storeFile", "OLLIN_FINANZAS_STORE_FILE")
    ?.let(rootProject::file)
    ?.takeIf { it.isFile }

/**
 * La version sale de CHANGELOG.md, no de este archivo.
 *
 * Un numero escrito a mano aqui se olvida: se publica la 1.2.0 con el build
 * todavia en 1.1.0, o al reves, y quien instala el APK ve una version que no
 * corresponde a las notas que leyo. Con el historial como unica fuente, subir
 * la version y explicar por que son el mismo gesto, y el flujo de publicacion
 * puede negarse a etiquetar algo que nadie documento.
 *
 * Se lee el primer encabezado `## [x.y.z]` del archivo. "Sin publicar" no casa
 * con el patron a proposito, asi que compilar mientras hay cambios sin
 * etiquetar sigue dando la ultima version publicada.
 */
val versionPublicada: Triple<Int, Int, Int> = run {
    val historial = rootProject.file("CHANGELOG.md")
    require(historial.isFile) { "Falta CHANGELOG.md: de ahi sale la version." }

    val encabezado = Regex("""^##\s+\[(\d+)\.(\d+)\.(\d+)]""", RegexOption.MULTILINE)
    val primero = encabezado.find(historial.readText())
        ?: error("CHANGELOG.md no tiene ningun encabezado `## [x.y.z]`.")

    val (mayor, menor, parche) = primero.destructured
    Triple(mayor.toInt(), menor.toInt(), parche.toInt())
}

val nombreDeVersion = versionPublicada.toList().joinToString(".")

/**
 * El entero que compara Android. Se deriva del semver con tres huecos de dos
 * cifras --1.2.3 es 10203-- para que crezca solo y nunca haya que acordarse de
 * subirlo aparte. Da margen hasta 99 versiones menores y 99 parches, de sobra
 * para una app que publica a mano, y ordena igual que el semver: cualquier
 * version posterior produce un entero mayor.
 */
val codigoDeVersion = versionPublicada.let { (mayor, menor, parche) ->
    mayor * 10_000 + menor * 100 + parche
}

/**
 * De donde se entera la app de que hay una version nueva.
 *
 * Es el sitio de GitHub Pages y no la API de GitHub: la API limita las
 * peticiones anonimas por IP --una red compartida las agota entre todos-- y
 * devuelve un objeto enorme del que solo se usan tres campos. Un JSON estatico
 * detras de un CDN no se cae, no se limita y se puede mirar con el navegador.
 *
 * Y es la direccion de `github.io` y no la del dominio propio aunque hoy la
 * primera redirija a la segunda. Esta va atada al repositorio y dura lo que el
 * repositorio; un dominio se renueva cada ano y se puede perder, y esta cadena
 * queda compilada dentro de cada APK: los que ya estan instalados no se pueden
 * corregir. El salto lo sigue el propio comprobador, exigiendo que el destino
 * tambien sea https.
 */
val urlDeActualizaciones = providers
    .gradleProperty("ollin.urlActualizaciones")
    .orNull
    ?: "https://carlosalbertoxw.github.io/ollin-finanzas/version.json"

android {
    namespace = "com.carlosalbertoxw.ollin.finanzas"
    // 37 para compilar y 36 para el comportamiento: AndroidX 2026.09 exige
    // compilar contra la API 37, pero subir targetSdk cambia como se porta la
    // app en el telefono, y eso va aparte y con sus propias pruebas.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carlosalbertoxw.ollin.finanzas"
        minSdk = 26
        targetSdk = 36
        versionCode = codigoDeVersion
        versionName = nombreDeVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "URL_ACTUALIZACIONES", "\"$urlDeActualizaciones\"")
    }

    androidResources {
        // La app esta escrita en espanol; no se empaquetan los recursos de las
        // bibliotecas en los otros ochenta idiomas.
        localeFilters += listOf("es")
    }

    signingConfigs {
        create("release") {
            if (almacenDeClaves != null) {
                storeFile = almacenDeClaves
                storePassword = credencial("storePassword", "OLLIN_FINANZAS_STORE_PASSWORD")
                keyAlias = credencial("keyAlias", "OLLIN_FINANZAS_KEY_ALIAS")
                keyPassword = credencial("keyPassword", "OLLIN_FINANZAS_KEY_PASSWORD")
            }
            // v1 no: minSdk 26 ya entiende v2, y firmar tambien el zip viejo
            // solo agrega una firma que nadie verifica.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release").takeIf { almacenDeClaves != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // Solo para URL_ACTUALIZACIONES: la direccion que consulta la app tiene
        // que quedar dentro del APK, y este es el unico camino para ponerla ahi
        // sin escribirla a mano en el codigo.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // Un aviso que no rompe nada no se lee. Los que Lint marca como error
        // --fugas de contexto, APIs por encima del minSdk, permisos que faltan--
        // son cosas que en esta app se notarian en el telefono de alguien.
        abortOnError = true
        warningsAsErrors = false
        // Tambien lo que traen las bibliotecas: un permiso o una API que entra
        // por una dependencia llega al APK igual que uno escrito aqui.
        checkDependencies = true
        // La app es monolingue por decision explicita (localeFilters = "es"),
        // asi que las quejas por traducciones ausentes son ruido.
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Los esquemas exportados viajan como assets de la suite instrumentada.
    // MigrationTestHelper los lee de ahi para comparar la base migrada contra
    // lo que Room espera; sin esta linea no encuentra ninguno y las pruebas de
    // migracion pasan sin comprobar nada.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/*
 * El inventario de lo que viaja dentro del APK (SBOM, en CycloneDX).
 *
 * Solo `releaseRuntimeClasspath`: lo que compila, prueba o depura no llega al
 * telefono, y meterlo en el inventario esconderia lo que importa entre cientos
 * de bibliotecas de Gradle y de pruebas. El flujo de publicacion lo adjunta a
 * cada release, para que ante una alerta futura --SQLite dentro de SQLCipher,
 * algo que arrastre AndroidX-- se pueda saber que version exacta llevaba cada
 * APK sin reconstruirlo.
 */
tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("releaseRuntimeClasspath")
    projectType = Component.Type.APPLICATION
    componentGroup = "com.carlosalbertoxw"
    componentName = "ollin-finanzas"
    componentVersion = nombreDeVersion
    jsonOutput = layout.buildDirectory.file("reports/sbom/ollin-finanzas.cdx.json")
    xmlOutput.unsetConvention()
}

/*
 * Las licencias que pueden viajar dentro del APK.
 *
 * El APK redistribuye cada biblioteca que lleva dentro, y cada una pone sus
 * condiciones. Una nueva --o una transitiva que cambia de licencia al subir de
 * version-- con condiciones que MIT no admite, como GPL, haria del APK algo que
 * no se puede publicar como se publica. `licenseeAndroidRelease` corre en
 * pruebas.yml y falla si aparece una que no este en esta lista.
 *
 * Tambien sirve para mantener al dia `res/raw/licencias_terceros.txt`: el
 * informe `build/reports/licensee/androidRelease/artifacts.json` dice que lleva
 * de verdad el APK.
 */
licensee {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
    // Su POM no declara un identificador SPDX, solo esta direccion. Lo que hay
    // en ella es una BSD de tres clausulas, la misma que se reproduce en
    // licencias_terceros.txt. Si cambiara de URL, licensee volveria a fallar,
    // y eso es justo lo que se quiere: mirarla otra vez.
    allowUrl("https://www.zetetic.net/sqlcipher/license/") {
        because("BSD de tres clausulas, reproducida en licencias_terceros.txt")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // No la usa la app directamente: la trae Navigation. Va explicita para
    // subirla a la que pide room-testing en las pruebas de migracion; ver
    // libs.versions.toml.
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    // Debe ir explicito: sin el, biometric fija fragment en 1.2.5 y los
    // launchers de ActivityResult truenan al abrirse. Ver libs.versions.toml.
    implementation(libs.androidx.fragment)
    implementation(libs.sqlcipher.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // MigrationTestHelper: corre las migraciones de verdad contra los esquemas
    // exportados en app/schemas/. Ver MigracionesTest.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

/*
 * ktlint: sangria, imports, espacios y largo de linea. Las reglas que piden
 * otro acomodo de lineas estan apagadas en .editorconfig, con su motivo.
 *
 * Sin baseline: el que habia congelaba 275 lineas de mas de 100 columnas por su
 * numero de linea, asi que cualquier cambio que las moviera las hacia aparecer
 * como nuevas. Ya no queda ninguna, y CI falla con la primera. La unica excepcion
 * es XlsxEscritor, que la declara en su cabecera: sus partes OOXML van tal cual
 * las define la norma. Ver docs/desarrollo.md.
 */
ktlint {
    version.set(libs.versions.ktlint)
}
