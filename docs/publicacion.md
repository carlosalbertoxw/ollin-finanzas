# Publicación

Publicar una versión de Ollin Finanzas es empujar una etiqueta. Todo lo demás lo hace [`publicacion.yml`](../.github/workflows/publicacion.yml).

```bash
git tag -a v1.1.0 -m "Ollin Finanzas 1.1.0"
git push origin v1.1.0
```

A partir de ahí: se comprueba que la etiqueta coincide con lo que encabeza el `CHANGELOG`, se pasan las pruebas, se compila el APK firmado, se crea la release con las notas de esa versión y se lanza el despliegue del [sitio](sitio.md) para que anuncie la descarga nueva.

Ese último despliegue se lanza sobre `main` con `gh workflow run` y corre aparte, así que el flujo de publicación termina sin esperarlo. Es a propósito: el entorno `github-pages` solo admite despliegues desde la rama por omisión, y además el contenido del sitio debe salir de `main` y no del árbol al que apunte una etiqueta. Ver [el sitio](sitio.md#siempre-desde-main-nunca-desde-la-etiqueta).

## El CHANGELOG manda

No hay ningún número de versión escrito a mano en el proyecto. [`app/build.gradle.kts`](../app/build.gradle.kts) lee el primer encabezado `## [x.y.z]` de [`CHANGELOG.md`](../CHANGELOG.md) y de ahí saca las dos cosas:

- **`versionName`** es ese número tal cual.
- **`versionCode`** se deriva con tres huecos de dos cifras: `1.2.3` → `10203`. Crece solo, ordena igual que el semver y nunca hay que acordarse de subirlo aparte. Da margen hasta 99 versiones menores y 99 parches.

Un número escrito a mano en el build se olvida: se publica la 1.2.0 con el build todavía en 1.1.0, y quien instala el APK ve una versión que no corresponde a las notas que leyó. Con el historial como única fuente, subir la versión y explicar por qué son el mismo gesto.

**El flujo se niega a publicar una etiqueta que no encabece el `CHANGELOG`.** No es burocracia: si la etiqueta dijera `v1.2.0` y el archivo encabezara con `1.1.0`, el APK saldría marcado como 1.1.0 dentro de una release llamada 1.2.0. Es la confusión más cara posible, porque se descubre meses después mirando un teléfono.

### Preparar una versión

1. Renombrar `## [Sin publicar]` a `## [1.1.0] - 2026-09-20` y abrir una sección `## [Sin publicar]` vacía encima.
2. Actualizar las referencias de enlace del final del archivo.
3. Empujar a `main` y esperar a que pase [`pruebas.yml`](../.github/workflows/pruebas.yml).
4. Etiquetar.

## Los secretos

El flujo necesita cuatro, en *Settings → Secrets and variables → Actions*, **como secretos del repositorio y no de un entorno**: el job que firma no declara `environment:`, así que un secreto de entorno le llega vacío.

| Secreto | Qué es |
|---|---|
| `OLLIN_FINANZAS_KEYSTORE_BASE64` | El `.jks` completo, codificado en base64 |
| `OLLIN_FINANZAS_STORE_PASSWORD` | Contraseña del almacén |
| `OLLIN_FINANZAS_KEY_ALIAS` | `ollin-finanzas` |
| `OLLIN_FINANZAS_KEY_PASSWORD` | Contraseña de la clave |

El almacén viaja en base64 porque un secreto de Actions es texto y un `.jks` es binario:

```bash
base64 -w 0 ollin-finanzas-release.jks > almacen.b64
```

Se pega el contenido de `almacen.b64` y **se borra el archivo**. En el runner se restaura en `$RUNNER_TEMP`, fuera del árbol de trabajo, para que no pueda acabar dentro de un artefacto por descuido.

Los nombres llevan la app completa y no un `OLLIN_` a secas: Ollin Finanzas se publica aparte y con su propio almacén, y unos nombres genéricos harían que cada app tomara la llave de la otra sin avisar.

### Sin secretos no se publica

Compilando en local, la ausencia de credenciales **no** falla la compilación: sale `app-release-unsigned.apk` y sigue adelante. Es a propósito, para que quien solo quiere comprobar que R8 no rompió nada no necesite el almacén.

En el flujo de publicación eso sería un desastre silencioso —una release con un APK que no se puede instalar—, así que hay tres redes.

**Antes de compilar** se comprueban los cuatro secretos juntos y se listan todos los que falten de una vez, para arreglarlos en un viaje. Un secreto ausente en Actions es la cadena vacía, no un error: si se deja pasar, la compilación tarda dos minutos en morir dentro de AGP con un `NullPointerException` que no nombra el secreto ni dice que falte uno.

En el mismo paso se abre el almacén con `keytool` para comprobar que el base64 corresponde a un `.jks` de verdad, que la contraseña lo abre y que el alias existe. Tarda un segundo y ahorra los dos minutos que R8 necesita para llegar al momento de firmar y descubrirlo. La contraseña va por `-storepass:env`, nunca por la línea de comandos.

**Después de compilar** se pasa `apksigner verify --print-certs` sobre el APK, antes de crear la release.

## Qué se publica

Solo el **APK** y un `checksums.txt` con su SHA-256.

El `.aab` se compila —un fallo de bundling es un fallo igual y conviene verlo— pero no se adjunta: no se instala en ningún teléfono, solo sirve para subirlo a Play, y una descarga que no hace lo que promete confunde a quien llega de fuera. Si algún día hace falta, `./gradlew bundleRelease`.

La huella se publica junto al archivo para que cualquiera pueda comprobar que lo que bajó es lo que salió de esa compilación. El [sitio](sitio.md) la repite, pero calculada sobre el archivo ya publicado.

## Qué se comprueba antes

| Comprobación | Dónde | Bloquea |
|---|---|---|
| Etiqueta contra `CHANGELOG` | `publicacion.yml` | Sí |
| Pruebas unitarias, Lint, `assembleRelease` | `pruebas.yml`, invocado tal cual | Sí |
| Actualizar sobre la versión anterior | `publicacion.yml` | Sí |
| `MigracionesTest` en emulador | `publicacion.yml` | Todavía no existe |
| Suite de interfaz completa | [`pruebas-instrumentadas.yml`](../.github/workflows/pruebas-instrumentadas.yml) | No |

Las pruebas son **el mismo flujo** que corre en cualquier pull request, invocado con `workflow_call` en vez de copiado. Una etiqueta no puede pasar por una comprobación más floja que un cambio cualquiera, y dos copias de los mismos pasos divergen.

Las migraciones bloquearán y las de interfaz no. Una migración equivocada deja la app sin abrir en el teléfono de quien actualiza y no hay forma de arreglarlo desde fuera; las de pantalla dependen de animaciones y relojes, y su intermitencia no puede ser lo que impida publicar una corrección. Esas corren solas los lunes y a mano cuando se ha tocado una pantalla.

La prueba de actualización entró en ese grupo después de que la 1.0.1 saliera cerrándose al abrirse en los teléfonos que venían de la 1.0.0: instala la versión de la etiqueta anterior, la abre para que escriba sus preferencias, instala la nueva encima y comprueba que sigue abriendo. Ver [desarrollo](desarrollo.md#la-prueba-de-actualización).

El esquema sigue en su versión inicial, así que no hay ninguna migración que ejecutar: el hueco está reservado en [`publicacion.yml`](../.github/workflows/publicacion.yml) con lo que tiene que ir ahí, y mientras tanto [`EsquemaDeBaseTest`](../app/src/test/java/com/carlosalbertoxw/ollin/finanzas/EsquemaDeBaseTest.kt) vigila en la JVM que la cadena no tenga huecos. Ver [modelo de datos](modelo-de-datos.md#migraciones).

## La identidad de la app

El `applicationId` y el `namespace` son `com.carlosalbertoxw.ollin.finanzas`, y el código fuente vive bajo ese mismo paquete.

Lleva el dominio de quien publica y no un `mx.ollin` a secas: `mx.ollin` no está respaldado por ningún dominio registrado, y el `applicationId` es un identificador global —quien registre `ollin.mx` antes podría reclamarlo—. `com.carlosalbertoxw` sí es un espacio propio, y deja sitio para que Ollin Finanzas cuelgue del mismo tronco sin colisionar.

**Ya publicada, esta cadena no se puede cambiar.** Para Android una app con otro `applicationId` es otra app: no actualiza a la instalada, sino que se instala al lado, y el libro de la primera se queda donde estaba, cifrada con una llave del Keystore que la nueva no puede leer.

## Por qué hace falta firmar

Android no instala un APK sin firma, y la firma es lo que ata una actualización a la app que ya está instalada: una versión firmada con otra llave es, para el sistema, una app distinta, y se niega a actualizar. **No hay forma de recuperar un almacén de claves perdido**; la única salida sería publicar con otro `applicationId` y pedirle a cada persona que reinstale desde cero, perdiendo su libro.

Guarda el `.jks` y sus contraseñas en un gestor de contraseñas o en una copia fuera de este equipo. No entran al repositorio: `.gitignore` bloquea `*.jks`, `*.keystore` y `keystore.properties` precisamente porque con ellos cualquiera puede publicar una actualización falsa que Android instalaría sin protestar.

### Crear el almacén

Una sola vez, en la raíz del proyecto:

```bash
"$HOME/.jdks/jbr-21.0.11/bin/keytool" -genkeypair -v -keystore ollin-finanzas-release.jks -alias ollin-finanzas -keyalg RSA -keysize 4096 -validity 10000
```

Los 10 000 días (unos 27 años) son la recomendación de Google: una llave vencida deja de servir para publicar actualizaciones.

**El nombre lleva la app completa, no solo `ollin`.** Ollin Finanzas y Ollin Finanzas son dos aplicaciones distintas, cada una con su `applicationId` y su propio almacén. Un archivo `ollin-release.jks` no dice a cuál pertenece, y confundirlos al firmar no se nota hasta que la actualización se niega a instalarse. **Nunca firmes las dos apps con el mismo almacén:** una llave comprometida se llevaría las dos por delante, y no hay forma de rotarla sin obligar a reinstalar.

## Publicar a mano

Sigue funcionando, para una emergencia o para probar el artefacto de release en un teléfono:

```bash
cp keystore.properties.example keystore.properties   # y rellenarlo
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew assembleRelease
```

Queda en `app/build/outputs/apk/release/app-release.apk`. Para saber con qué llave se va a firmar antes de compilar nada, sin escribir ninguna contraseña:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew :app:signingReport
```

Y para comprobar la firma de un APK ya construido:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Qué lleva la compilación de release

- **R8 con `isMinifyEnabled` e `isShrinkResources`.** Las reglas de [`proguard-rules.pro`](../app/proguard-rules.pro) conservan las entidades de Room —para que los nombres de columna no se ofusquen— y las clases de SQLCipher, a las que se llama desde JNI.
- **Solo recursos en español** (`localeFilters`), porque la app es monolingüe.
- **Firma v2 y v3, sin v1.** `minSdk` es 26 y ya entiende v2; firmar también el zip antiguo solo agrega una firma que nadie verifica.

## Antes de etiquetar

- Que `main` esté verde.
- Correr [`pruebas-instrumentadas.yml`](../.github/workflows/pruebas-instrumentadas.yml) a mano si se tocó alguna pantalla.
- Instalar el APK de release en un teléfono y abrirlo: R8 puede romper cosas que la compilación de depuración no enseña. Lo que más riesgo corre es que la base abra, por SQLCipher y Room.
- Si hay migración nueva, instalar **encima** de la versión anterior con datos dentro, no sobre una instalación limpia. Es el único camino que recorre la gente y el único que el emulador no reproduce del todo.
- Dejar el `CHANGELOG` como quedará publicado: ese texto es el cuerpo de la release y lo que la app enseña en *Acerca de*.
