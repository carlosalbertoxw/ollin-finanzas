import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Los datos de la firma viven en keystore.properties, fuera del repositorio.
// Se lee con providers.fileContents y no con File(...).readText() porque la
// cache de configuracion esta activa: el API de providers registra el archivo
// como entrada del build, asi que editarlo invalida la cache. Un File() suelto
// se leeria una vez y las ediciones posteriores se ignorarian en silencio.
val archivoFirma = rootProject.layout.projectDirectory.file("keystore.properties")
val propiedadesFirma = providers.fileContents(archivoFirma).asText.orNull?.let { texto ->
    Properties().apply { load(texto.reader()) }
}

if (propiedadesFirma == null) {
    logger.warn("Ollin: sin keystore.properties, el APK de release saldra SIN FIRMAR y no se podra instalar. Ver docs/desarrollo.md.")
}

android {
    namespace = "com.carlosalbertoxw.ollin.finanzas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carlosalbertoxw.ollin.finanzas"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("es")
    }

    signingConfigs {
        // Solo se crea si hay keystore.properties. Sin el, la configuracion
        // queda nula y el release sale sin firmar en vez de romper el build:
        // asi el proyecto sigue compilando en una maquina recien clonada.
        propiedadesFirma?.let { props ->
            val faltantes = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .filter { props.getProperty(it).isNullOrBlank() }
            require(faltantes.isEmpty()) {
                "keystore.properties existe pero le faltan claves: ${faltantes.joinToString()}"
            }

            val almacen = rootProject.file(props.getProperty("storeFile"))
            require(almacen.exists()) {
                "keystore.properties apunta a ${almacen.absolutePath}, que no existe."
            }

            create("release") {
                storeFile = almacen
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
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
            signingConfig = signingConfigs.findByName("release")
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
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

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
    implementation(libs.androidx.documentfile)
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
