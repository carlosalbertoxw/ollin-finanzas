# Auditoría del proyecto: ollin-finanzas

**Fecha:** 2026-10-01 · **Commit auditado:** `0921176` (main) · **Auditor:** Claude (skill auditoria-proyecto-software)

## 1. Resumen ejecutivo

Ollin Finanzas es una app Android de finanzas personales (Kotlin, Jetpack Compose, Room sobre SQLCipher) que funciona solo en el teléfono: no tiene cuentas, ni servidor, ni analítica. Se distribuye como APK firmado desde GitHub Releases y un sitio estático (Vite, GitHub Pages detrás de Cloudflare). Lo que hay que proteger son los datos financieros dentro del teléfono y la integridad del canal de distribución (la firma del APK y el sitio de descarga). La base técnica es muy buena: cifrado de la base con una llave envuelta en el Keystore, PIN con PBKDF2 y comparación en tiempo constante, `FLAG_SECURE`, respaldo automático desactivado, un lector de `.xlsx` endurecido, un CI que bloquea la publicación si fallan las pruebas o la actualización, y documentación de una calidad poco común. Los riesgos más serios están **fuera del código de la app**: el sitio de descarga responde por HTTP sin redirigir a HTTPS, y la cadena de suministro del pipeline de firma tiene poca protección.

**Calificación global:** 72 % — Aceptable con mejoras
**Hallazgos:** 0 críticos · 1 alto · 6 medios · 8 bajos · 1 informativo
**Controles no verificables desde el código:** 5 de 72 (además, 23 no aplican a una app local sin backend)

**Prioridades principales**
1. [H-01] El sitio de descarga se sirve por HTTP sin redirección: quien instala por primera vez puede recibir un APK manipulado.
2. [H-06] El job que firma la release usa acciones fijadas por etiqueta, y no hay verificación de dependencias de Gradle: es el camino para robar la llave de firma.
3. [H-02] La espera tras fallar el PIN se pierde al cerrar la app, así que matar el proceso da un intento gratis cada vez.
4. [H-05] El escaneo de secretos y la push protection están apagados en un repositorio público que tiene el `.jks` de release en la raíz del árbol de trabajo.
5. [H-03] Un `.xlsx` con una zip bomb en una sola parte tumba la app con `OutOfMemoryError` antes de que se aplique el límite de 64 MB.

## 2. Resultados por área

| # | Área | Cumple | Parcial | No cumple | N/A | No verif. | Puntaje |
|---|------|-------:|--------:|----------:|----:|----------:|--------:|
| 01 | Fundamentos del proyecto | 1 | 1 | 0 | 1 | 0 | 75 % |
| 02 | Diseño y arquitectura | 1 | 1 | 0 | 0 | 2 | 75 % |
| 03 | Documentación | 5 | 3 | 0 | 1 | 0 | 81 % |
| 04 | Pruebas y calidad | 2 | 2 | 0 | 0 | 0 | 75 % |
| 05 | Automatización y despliegue | 2 | 2 | 0 | 0 | 0 | 75 % |
| 06 | Observabilidad y operación | 1 | 1 | 0 | 4 | 0 | 75 % |
| 07 | Secretos y configuración | 3 | 0 | 0 | 0 | 0 | 100 % |
| 08 | Autenticación y sesiones | 2 | 1 | 0 | 2 | 1 | 83 % |
| 09 | Base de datos y autorización | 0 | 0 | 0 | 5 | 0 | — |
| 10 | Validación y protección de datos | 5 | 1 | 0 | 0 | 0 | 92 % |
| 11 | Archivos y APIs | 0 | 1 | 0 | 3 | 0 | 50 % |
| 12 | Seguridad web | 1 | 1 | 1 | 2 | 0 | 50 % |
| 13 | Dependencias y vigilancia | 0 | 2 | 3 | 1 | 2 | 20 % |
| 14 | Cumplimiento y aspectos legales | 3 | 2 | 0 | 0 | 0 | 80 % |

El área 09 no entra en el promedio: la base es local, de un solo usuario y sin servidor, así que no hay autorización entre usuarios ni registros ajenos que proteger.

<details>
<summary>Detalle de estado por control</summary>

| Control | Estado | Nota breve |
|---------|--------|------------|
| FUN-01 | ✅ Cumple | 36 commits con mensajes que explican el motivo; binarios, `.jks` y `local.properties` ignorados |
| FUN-02 | ⚠️ Parcial | Un solo autor, commits directos a `main`; la API confirma que `main` no tiene protección (H-08) |
| FUN-03 | ➖ N/A | App Android: el entorno lo fijan el Gradle Wrapper y el JDK documentado, no un contenedor |
| ARQ-01 | ❓ No verificable | No hay objetivos numéricos (tiempo de arranque, tamaño del APK, tope de movimientos) |
| ARQ-02 | ❓ No verificable | Hay decisiones razonadas en el README, pero no se ve si se discuten antes de implementar |
| ARQ-03 | ⚠️ Parcial | `docs/seguridad.md` y los comentarios cubren amenazas concretas (adb, MITM, root, zip/XML), pero falta un modelo explícito del canal de distribución |
| ARQ-04 | ✅ Cumple | Dependencias mínimas y justificadas: sin POI, sin OkHttp, sin Hilt (README, «Decisiones») |
| DOC-01 | ✅ Cumple | El README abre con qué es, para quién y qué no hace |
| DOC-02 | ✅ Cumple | JDK 17–21, SDK 36 y comandos documentados; `keystore.properties.example` |
| DOC-03 | ✅ Cumple | `docs/arquitectura.md` y el árbol del README |
| DOC-04 | ✅ Cumple | Decisiones con contexto y alternativas en el README y en los KDoc; sin formato ADR, suficiente a esta escala |
| DOC-05 | ➖ N/A | No hay API; el contrato de `version.json` está documentado de todos modos |
| DOC-06 | ⚠️ Parcial | No hay `CONTRIBUTING.md` en un repo público MIT; `docs/desarrollo.md` tiene las convenciones |
| DOC-07 | ⚠️ Parcial | La publicación está documentada; no hay procedimiento para una release mala ni para perder o filtrar la llave de firma (H-09) |
| DOC-08 | ✅ Cumple | CHANGELOG con semver, del que sale la versión del build; 5 tags |
| DOC-09 | ⚠️ Parcial | Documentación en el repo, pero con dos desajustes con el código (H-11) |
| PRU-01 | ✅ Cumple | 208 pruebas JVM: dinero, PIN, bloqueo, repositorio, calidad, Excel |
| PRU-02 | ✅ Cumple | Robolectric con Room en memoria, ida y vuelta de Excel, esquema de la base |
| PRU-03 | ⚠️ Parcial | 11 pruebas de interfaz (navegación y componentes) más la prueba de actualización; no cubren captura, importación ni desbloqueo (H-13) |
| PRU-04 | ⚠️ Parcial | Android Lint en CI y `.editorconfig`; sin ktlint ni detekt (H-12) |
| CI-01 | ✅ Cumple | Pruebas, Lint, R8, firma, release y sitio sin pasos manuales |
| CI-02 | ⚠️ Parcial | Los `\|\| true` están justificados y no afectan pruebas, pero sin protección de rama un rojo no impide empujar a `main` (H-08) |
| CI-03 | ✅ Cumple | Esquema exportado, `EsquemaDeBaseTest`, nunca `fallbackToDestructiveMigration` |
| CI-04 | ⚠️ Parcial | Releases etiquetadas con SHA-256; Android no permite bajar de versión y el «roll-forward» no está escrito (H-09) |
| OBS-01 a OBS-04 | ➖ N/A | No hay servicio desplegado |
| OBS-05 | ⚠️ Parcial | Sin reporte de fallos, por decisión de privacidad; lo compensa en parte la prueba de actualización (H-14) |
| OBS-06 | ✅ Cumple | Respaldo por `.xlsx` con recordatorio semanal; la restauración se prueba en `ExcelRoundTripTest` |
| SEC-01 | ✅ Cumple | No hay secretos versionados; `keystore.properties` y `*.jks` en `.gitignore` (comprobado con `git check-ignore`) |
| SEC-02 | ✅ Cumple | `git log --all` no muestra ningún `.jks` ni `keystore.properties` en el historial |
| SEC-03 | ✅ Cumple | Firma por variables de entorno y secretos de Actions; el `.jks` se escribe en `$RUNNER_TEMP` |
| AUT-01 | ➖ N/A | No hay sesiones ni servidor |
| AUT-02 | ✅ Cumple | PIN con PBKDF2-HMAC-SHA256 a 120 000 iteraciones, sal de 16 bytes y `MessageDigest.isEqual` |
| AUT-03 | ⚠️ Parcial | Hay espera exponencial, pero se pierde al reiniciar el proceso (H-02) |
| AUT-04 | ➖ N/A | No hay cookies |
| AUT-05 | ✅ Cumple | Vuelve a bloquear tras 60 s en segundo plano, medidos con reloj monótono; cambiar el candado exige la credencial actual |
| AUT-06 | ❓ No verificable | La cuenta de GitHub es el canal de publicación y guarda la llave de firma: ¿tiene MFA? |
| BD-01 a BD-05 | ➖ N/A | Base local de un solo usuario; consultas Room parametrizadas |
| VAL-01 | ✅ Cumple | El importador normaliza y repara al entrar; el repositorio impone las invariantes |
| VAL-02 | ✅ Cumple | Compose no interpreta HTML; el sitio usa `textContent`; el XML del `.xlsx` se escapa |
| VAL-03 | ✅ Cumple | Solo `@Query` de Room con parámetros; ningún `rawQuery` ni `execSQL` con interpolación |
| VAL-04 | ✅ Cumple | URL fija compilada, un solo salto y solo hacia https, respuesta de 64 K como máximo |
| VAL-05 | ⚠️ Parcial | Base cifrada; las notificaciones enseñan montos en la pantalla de bloqueo (H-04) |
| VAL-06 | ✅ Cumple | Solo se guarda lo necesario para el libro; el desarrollador no recibe datos |
| API-01 | ⚠️ Parcial | Rechaza DOCTYPE y limita a 64 MB, pero el límite se revisa después de descomprimir cada parte (H-03) |
| API-02 a API-04 | ➖ N/A | Los archivos los elige el usuario con el selector del sistema; no hay API ni endpoints |
| WEB-01 | ❌ No cumple | `http://carlosalbertoxw.com/ollin-finanzas/` responde 200 sin redirigir (H-01) |
| WEB-02 | ⚠️ Parcial | En producción Cloudflare envía CSP, HSTS y `frame-ancestors`; el repo no las define y la CSP permite `'unsafe-inline'` en scripts |
| WEB-03, WEB-04 | ➖ N/A | Sitio estático sin credenciales |
| WEB-05 | ✅ Cumple | Los errores se muestran con texto propio; la causa no se enseña al usuario (`XlsxLector.kt:100-104`) |
| DEP-01 | ⚠️ Parcial | `npm audit`: 0 vulnerabilidades; las alertas de Dependabot están activas, pero las dependencias de Gradle no se escanean en CI (H-07) |
| DEP-02 | ⚠️ Parcial | `npm ci` con lockfile; Gradle sin verificación de dependencias y acciones fijadas por etiqueta (H-06) |
| DEP-03 | ❌ No cumple | No hay `dependabot.yml` para actualizaciones de versión ni SBOM; BOM de Compose de abril de 2025 (H-07) |
| DEP-04 | ❌ No cumple | Sin CodeQL ni gitleaks; secret scanning y push protection apagados (H-05) |
| DEP-05 | ➖ N/A | No hay operaciones de servidor que vigilar |
| DEP-06 | ❓ No verificable | ¿Quién recibe y atiende las alertas de Dependabot? |
| DEP-07 | ❓ No verificable | No hay pruebas de seguridad externas registradas |
| DEP-08 | ❌ No cumple | Sin `SECURITY.md` ni plan ante una llave de firma filtrada (H-09) |
| LEG-01 | ✅ Cumple | MIT con dependencias Apache-2.0 y la licencia BSD de SQLCipher: compatibles |
| LEG-02 | ✅ Cumple | No se recogen datos personales; la exportación y la desinstalación cubren el borrado |
| LEG-03 | ⚠️ Parcial | El aviso de privacidad coincide con el código salvo por dónde se apaga la consulta (H-11) |
| LEG-04 | ✅ Cumple | El único tercero es GitHub Pages/Cloudflare, que recibe la IP de la consulta diaria; está documentado |
| LEG-05 | ⚠️ Parcial | El APK no incluye avisos de licencias de terceros (H-10) |

</details>

## 3. Hallazgos

### [H-01] El sitio de descarga se sirve por HTTP sin redirigir a HTTPS — 🟠 Alta
- **Control:** WEB-01 · HTTPS forzado
- **Evidencia:** `curl -I http://carlosalbertoxw.com/ollin-finanzas/` responde `HTTP/1.1 200 OK`, sin redirección. Envía `Strict-Transport-Security`, pero los navegadores ignoran esa cabecera cuando llega por HTTP, y `hstspreload.org` devuelve `status: unknown` para el dominio, así que no está en la lista precargada. La API de Pages reporta `https_enforced: false` y `html_url: http://carlosalbertoxw.com/ollin-finanzas/`. La huella SHA-256 que muestra la página (`sitio.yml`) viaja por el mismo canal, y en ningún lugar se publica la huella del certificado de firma (`grep` en README, `docs/` y `web/`).
- **Riesgo:** quien llega por primera vez desde un enlace `http://` (o escribiendo el dominio a mano) en una red hostil puede recibir una página alterada que apunte a un APK troyanizado de una app financiera. La firma de Android solo protege a quien ya tiene instalada la versión legítima; la primera instalación no tiene defensa. La app no está expuesta: consulta `https://…github.io` y solo sigue redirecciones hacia https.
- **Recomendación:**
  1. En Cloudflare, activa *SSL/TLS → Edge Certificates → Always Use HTTPS* (o una regla de redirección 301 de http a https).
  2. Cuando todo el dominio y sus subdominios respondan por HTTPS, envía `carlosalbertoxw.com` a hstspreload.org. La cabecera ya trae `preload; includeSubDomains`.
  3. Publica la huella SHA-256 del **certificado de firma** (`apksigner verify --print-certs`) en el README y en las notas de cada release. GitHub es un canal distinto del sitio, y con eso cualquiera puede comprobar el APK con `apksigner` o AppVerifier.
- **Esfuerzo estimado:** Bajo

### [H-06] El pipeline que firma la release depende de código de terceros sin fijar — 🟡 Media
- **Control:** DEP-02 · Versiones fijadas e integridad verificada
- **Evidencia:** todos los `uses:` usan etiquetas mutables (`actions/checkout@v4`, `gradle/actions/setup-gradle@v4`, `reactivecircus/android-emulator-runner@v2`…). El job `publicar` (`.github/workflows/publicacion.yml`) corre `setup-gradle@v4` y luego `./gradlew assembleRelease` con `OLLIN_FINANZAS_STORE_PASSWORD` y `OLLIN_FINANZAS_KEY_PASSWORD` en el entorno. No existe `gradle/verification-metadata.xml`, y `gradle-wrapper.properties` no lleva `distributionSha256Sum`. Los secretos de firma son secretos del repositorio, no de un *environment* con aprobación.
- **Riesgo:** si alguien compromete una acción, un plugin o una dependencia resuelta en ese build, puede llevarse el almacén y sus contraseñas. Con ellos publicaría actualizaciones que **todas** las instalaciones aceptarían como legítimas, y no hay forma de revocar la llave sin cambiar el `applicationId`.
- **Recomendación:**
  1. Fija cada acción a su SHA completo (`uses: actions/checkout@<sha> # v4.x.y`) y deja que Dependabot (H-07) lo mantenga al día.
  2. Genera la verificación de dependencias con `./gradlew --write-verification-metadata sha256 assembleRelease` y versiona el archivo.
  3. Agrega `distributionSha256Sum` al wrapper.
  4. Pasa los cuatro secretos a un environment `release` limitado a tags `v*`, con revisión obligatoria.
- **Esfuerzo estimado:** Medio

### [H-02] La espera tras fallar el PIN se pierde al reiniciar la app — 🟡 Media
- **Control:** AUT-03 · Límite de intentos de autenticación
- **Evidencia:** `data/seguridad/ControlBloqueo.kt:44` guarda `_esperaHasta` solo en memoria, con valor inicial `0L`. El `init` (`:52-58`) recupera `pinFallos` del disco, pero no recalcula la espera. `BloqueoPantalla.kt:118` la lee con `segundosDeEspera()`, que da 0 tras un arranque nuevo. El diálogo para cambiar o quitar el PIN (`AjustesPantalla.kt:619`) no aplica ningún freno, aunque ahí la app ya está desbloqueada.
- **Riesgo:** quien tenga el teléfono desbloqueado y la app con PIN puede cerrarla desde Recientes después de cada fallo y conseguir un intento sin esperar. Los 10 000 PIN de 4 dígitos pasan de ser inviables a costar unas horas de toques. El comentario de `registraFalloDePin` ya prevé que «cerrar la app sería la forma de reiniciarlo», pero eso solo se cumple para el contador.
- **Recomendación:** en el primer `collect` del `init`, si `pinFallos > FALLOS_DE_GRACIA`, haz `_esperaHasta.value = reloj() + esperaMillis(pinFallos)`. Esto se recalcula en cada arranque: es más estricto, no se puede evitar y no necesita guardar marcas de tiempo. Agrega a `ControlBloqueoTest` el caso «un control nuevo con 6 fallos guardados arranca con espera».
- **Esfuerzo estimado:** Bajo

### [H-03] Una zip bomb en una sola parte del `.xlsx` tumba la app — 🟡 Media
- **Control:** API-01 · Subidas restringidas por tamaño y contenido
- **Evidencia:** `data/excel/XlsxLector.kt:88` hace `zip.readBytes()` sobre la parte completa y **después** suma y compara con `LIMITE_BYTES` (`:89-92`). Una sola parte `.xml` de unos KB comprimidos que se expande a varios GB se carga entera en memoria. El `OutOfMemoryError` resultante es un `Error`, no una `Exception`, así que se salta el `catch` de `:98` y cierra la app. `XlsxLectorSeguridadTest` cubre DOCTYPE, pero no esto.
- **Riesgo:** un «respaldo» recibido por mensajería cierra la app al importarlo. No se pierden datos, porque la importación es todo o nada, pero es una denegación de servicio trivial y deja al usuario sin entender qué pasó. Se baja de Alta a Media porque el usuario tiene que elegir el archivo y el daño se limita a un cierre.
- **Recomendación:** sustituye `readBytes()` por una copia por bloques que corte al pasar el presupuesto que queda (`LIMITE_BYTES - total`) y lance `ArchivoInvalido`. Pon también un tope al número de partes y una prueba con una parte de 100 MB de ceros comprimida.
- **Esfuerzo estimado:** Bajo

### [H-04] Las notificaciones muestran montos en la pantalla de bloqueo — 🟡 Media
- **Control:** VAL-05 · Protección de datos sensibles
- **Evidencia:** `data/notify/Recordatorios.kt:186-192` construye la notificación sin `setVisibility` ni `setPublicVersion`, con el nombre del compromiso como título y `"${Dinero.formatea(montoCentavos)} …"` como texto (`:229-233`).
- **Riesgo:** con el ajuste más común de Android («mostrar todo el contenido»), cualquiera que vea el teléfono bloqueado lee «Renta · $8,500 vence el…». Eso contradice el candado y el `FLAG_SECURE` que la app pone precisamente para que no se vean los montos.
- **Recomendación:** usa `setVisibility(NotificationCompat.VISIBILITY_PRIVATE)` con un `setPublicVersion(...)` de texto genérico («Tienes un pago por vencer»), o directamente `VISIBILITY_SECRET` cuando `modoBloqueo != NINGUNO`.
- **Esfuerzo estimado:** Bajo

### [H-05] Secret scanning y push protection apagados en un repo público — 🟡 Media
- **Control:** DEP-04 · SAST y escaneo de secretos
- **Evidencia:** `gh api repos/carlosalbertoxw/ollin-finanzas` → `secret_scanning: disabled` y `secret_scanning_push_protection: disabled`. En la raíz del árbol de trabajo están `ollin-finanzas-release.jks` y `keystore.properties` con las contraseñas. Están ignorados, pero a un `git add -f` o a un cambio en `.gitignore` de quedar publicados. No hay CodeQL, Semgrep ni gitleaks en `.github/workflows/`.
- **Riesgo:** si la llave de firma se filtra en un repo público, cualquiera puede publicar actualizaciones maliciosas de la app.
- **Recomendación:**
  1. Activa *Settings → Code security → Secret scanning* y *Push protection* (gratis en repos públicos).
  2. Activa CodeQL con *default setup*, que cubre Java/Kotlin.
  3. Saca el `.jks` y `keystore.properties` del árbol del proyecto (por ejemplo a `~/.android-keys/`) y apunta `storeFile` a esa ruta.
- **Esfuerzo estimado:** Bajo

### [H-07] Sin actualizaciones automáticas de dependencias ni SBOM — 🟡 Media
- **Control:** DEP-03 · SBOM y actualizaciones automatizadas (y DEP-01)
- **Evidencia:** no existe `.github/dependabot.yml`; solo están activas las *security updates*. `gradle/libs.versions.toml`: `composeBom = "2025.04.01"`, `agp = "8.10.0"`, `kotlin = "2.1.20"`, `sqlcipher = "4.6.1"`, `biometric = "1.1.0"`. Esta última es la que obliga al arreglo de `fragment`. El gráfico de dependencias de GitHub no resuelve bien Gradle sin la *dependency submission*, así que las alertas pueden no cubrir las dependencias transitivas.
- **Riesgo:** SQLCipher incluye su propio SQLite en código nativo, y una versión vieja arrastra sus CVE. Además, una actualización de muchas versiones de golpe es más difícil de hacer y de revisar.
- **Recomendación:** crea `dependabot.yml` con los ecosistemas `gradle`, `npm` (`/web`) y `github-actions`, en frecuencia semanal y con agrupación. Agrega `gradle/actions/dependency-submission` a `pruebas.yml`. Revisa primero SQLCipher y `biometric`; si con una versión nueva de `biometric` ya no hace falta fijar `fragment`, quita la línea de `fragment`. Opcional: un SBOM CycloneDX adjunto a cada release.
- **Esfuerzo estimado:** Bajo para configurar; Medio para la primera ronda de actualizaciones

### [H-08] `main` y los tags no tienen protección — 🔵 Baja
- **Control:** FUN-02 / CI-02
- **Evidencia:** `gh api …/branches/main/protection` → `Branch not protected`. Los 36 commits son directos, de un solo autor y sin merges.
- **Riesgo:** con un solo mantenedor el riesgo es bajo, y la publicación vuelve a correr las pruebas. Aun así, `main` puede quedar en rojo, y cualquier credencial con permiso de escritura puede crear un tag `v*` que dispare una release firmada.
- **Recomendación:** crea un ruleset para `main` que exija el check «Pruebas» y otro para tags `v*` que restrinja quién puede crearlos.
- **Esfuerzo estimado:** Bajo

### [H-09] Sin `SECURITY.md` ni procedimiento ante una llave de firma perdida o filtrada — 🔵 Baja
- **Control:** DEP-08 / DOC-07 / CI-04
- **Evidencia:** no existe `SECURITY.md`. `docs/publicacion.md` no explica qué hacer con una release defectuosa (Android no permite volver a una versión anterior; solo queda publicar una más nueva) ni con una llave comprometida o perdida.
- **Riesgo:** quien encuentre una vulnerabilidad no tiene un canal privado para reportarla. Y si se pierde la llave, todos los usuarios tienen que reinstalar con otro `applicationId`, y en esa reinstalación pierden los datos que no hayan exportado.
- **Recomendación:** crea un `SECURITY.md` corto y activa *Private vulnerability reporting*. En `docs/publicacion.md` añade dos procedimientos: corregir publicando una versión nueva (subir el parche, etiquetar y marcar la release mala como *pre-release*) y qué hacer si se filtra la llave. Confirma que hay una copia del `.jks` fuera de este equipo.
- **Esfuerzo estimado:** Bajo

### [H-10] El APK no incluye los avisos de licencias de terceros — 🔵 Baja
- **Control:** LEG-05
- **Evidencia:** ni `AcercaDePantalla.kt`, ni `res/`, ni el sitio mencionan SQLCipher, Zetetic ni Apache (`grep` sin resultados).
- **Riesgo:** la licencia Community de SQLCipher (tipo BSD) exige reproducir su aviso en las distribuciones binarias, y las dependencias Apache-2.0 piden conservar sus `NOTICE`.
- **Recomendación:** agrega una sección «Licencias de terceros» en *Acerca de*, con texto estático para no sumar dependencias, que incluya el aviso de SQLCipher y la lista de bibliotecas Apache-2.0.
- **Esfuerzo estimado:** Bajo

### [H-11] La documentación dice cosas que el código no hace — 🔵 Baja
- **Control:** DOC-09 / LEG-03
- **Evidencia:**
  - `docs/seguridad.md:5` y `web/index.html` (sección Privacidad) dicen que la consulta de actualizaciones «se apaga desde *Acerca de*». El interruptor está en Ajustes (`AjustesPantalla.kt:333-336`), como dice correctamente el README (`:17-18`).
  - `docs/seguridad.md:13` afirma que la llave «no sale del dispositivo, ni con root». Es cierto que no se puede extraer, pero con root se puede pedir al Keystore que la desenvuelva usando la identidad de la app, así que con root la base sí se puede leer.
- **Riesgo:** el aviso de privacidad pierde precisión, y la segunda frase promete más protección de la que existe.
- **Recomendación:** corrige las dos frases. Por ejemplo: «la llave no se puede copiar fuera del teléfono; en un teléfono con root, un atacante puede usarla desde ahí».
- **Esfuerzo estimado:** Bajo

### [H-12] Sin formateador ni análisis estático de Kotlin en CI — 🔵 Baja
- **Control:** PRU-04
- **Evidencia:** `pruebas.yml` corre `lintDebug`; hay `.editorconfig`, pero no ktlint, detekt ni spotless.
- **Recomendación:** agrega ktlint (plugin `org.jlleitschuh.gradle.ktlint`) o detekt a la misma invocación de Gradle. El `.editorconfig` ya define las reglas.
- **Esfuerzo estimado:** Bajo

### [H-13] Las pruebas de interfaz no recorren los flujos críticos — 🔵 Baja
- **Control:** PRU-03
- **Evidencia:** `app/src/androidTest` tiene 11 pruebas (`NavegacionTest`, `ComponentesComunesTest`). No hay pruebas de captura, transferencia, importación ni desbloqueo con PIN.
- **Recomendación:** agrega pruebas de Compose para el desbloqueo con PIN (incluida la espera de H-02) y para capturar un movimiento. Pueden seguir siendo semanales y no bloquear.
- **Esfuerzo estimado:** Medio

### [H-14] Ningún medio para enterarse de un fallo en producción — 🔵 Baja
- **Control:** OBS-05
- **Evidencia:** no hay reporte de fallos, y es a propósito («no hay analítica»). La regresión de la 1.0.1 (`actualizacion.yml:3-9`) solo se descubrió después de publicarla.
- **Recomendación:** sin romper la promesa de privacidad, guarda localmente la última excepción no capturada y ofrece en *Acerca de* un botón «Copiar informe de error» que el usuario decida compartir.
- **Esfuerzo estimado:** Bajo

### [H-15] Los `SUMIFS` exportados interpretan comodines en los nombres — 🔵 Baja
- **Control:** VAL-01 (corrección de datos exportados)
- **Evidencia:** `data/excel/ExportadorExcel.kt:234-240` usa la celda con el nombre de la cuenta como criterio (`SUMIFS(…, $A$n)`). En `SUMIFS`, un criterio con `*`, `?` o `~`, o que empiece con `<`, `>` o `=`, se trata como comodín u operador, no como texto literal.
- **Riesgo:** una cuenta llamada, por ejemplo, «Ahorro*» suma también «Ahorro 2» cuando la hoja se recalcula (`fullCalcOnLoad`). El valor guardado en caché es correcto, pero el recalculado no.
- **Recomendación:** rechaza esos caracteres en los nombres de cuentas y categorías, o usa una coincidencia exacta como `SUMPRODUCT((rango=$A$n)*cantidad)`. Agrega un caso en `ExportadorBordesTest`.
- **Esfuerzo estimado:** Bajo

### [H-16] Buenas prácticas destacables — ⚪ Informativa
- La base está cifrada con SQLCipher, con una frase aleatoria envuelta en AES-GCM por una llave del Keystore y sin camino sin cifrar (`OllinDatabase.kt`, `LlaveBase.kt`).
- El respaldo en la nube y la transferencia entre dispositivos excluyen la base, la llave y los ajustes (`res/xml/*`).
- El bloqueo arranca cerrado, usa `FLAG_SECURE` y mide el periodo de gracia con reloj monótono.
- El lector de `.xlsx` rechaza DOCTYPE y bombas de entidades, y los mensajes de error no exponen rutas internas.
- La red está reducida al mínimo: una URL compilada, https en cada salto, tope de 64 K y nada se descarga ni se instala solo.
- Los receivers no están exportados, los `PendingIntent` son `FLAG_IMMUTABLE` y la ruta inicial se compara contra una lista blanca.
- La versión sale del CHANGELOG, la etiqueta se valida contra él, la firma se comprueba antes de publicar y la prueba de actualización bloquea la release.
- `npm audit` del sitio: 0 vulnerabilidades.

## 4. Preguntas para el equipo

| Control | Pregunta |
|---------|----------|
| AUT-06 | ¿La cuenta de GitHub `carlosalbertoxw` (dueña del canal de releases y de los secretos de firma) y la de Cloudflare tienen MFA con llave física o TOTP? |
| OBS-06 / SEC | ¿Existe una copia del `ollin-finanzas-release.jks` y de sus contraseñas fuera de este equipo (gestor de contraseñas o medio cifrado sin conexión)? Si se pierde, ninguna instalación podrá volver a actualizarse. |
| ARQ-01 | ¿Hay objetivos concretos, como tiempo de arranque, tamaño máximo del APK o número de movimientos que la app debe manejar con fluidez? |
| ARQ-02 | ¿Los cambios de esquema o de arquitectura se escriben antes de implementarse, o solo después en el README? |
| DEP-06 | ¿Quién recibe las alertas de Dependabot y en qué plazo se atienden? |
| DEP-07 | ¿Se ha hecho alguna revisión externa del APK, por ejemplo con MobSF o una prueba en un dispositivo con root? |

## 5. Plan de remediación

**Inmediato (antes de la próxima release):**
- H-01: activar *Always Use HTTPS* en Cloudflare y publicar la huella del certificado de firma.
- H-05: activar secret scanning y push protection, y sacar el `.jks` del árbol del proyecto.
- Responder la pregunta del respaldo del `.jks`.

**Corto plazo (2 a 4 semanas):**
- H-06 y H-07 juntos: acciones fijadas por SHA, `dependabot.yml`, verificación de dependencias de Gradle, environment `release` para los secretos y actualización de SQLCipher y `biometric`.
- H-02, H-03 y H-04: tres arreglos pequeños en la app, con su prueba cada uno; caben en una misma 1.1.1.

**Mediano plazo:**
- Gobierno del repositorio: H-08, H-09 (`SECURITY.md` y procedimientos) y H-10 (licencias).
- Calidad: H-12, H-13, H-14 y H-15.
- Documentación: H-11.
- Valorar el envío del dominio a la lista de HSTS preload cuando todos los subdominios sirvan HTTPS.

## 6. Seguimiento de la remediación (2026-10-01, rama `auditoria-correcciones`)

| Hallazgo | Estado | Qué se hizo / qué falta |
|---|---|---|
| H-01 | ✅ Resuelto | Huella del certificado en el README, el sitio y las notas de cada release; la publicación falla si el APK va firmado con otra llave. *Always Use HTTPS* activo en Cloudflare: comprobado el 2026-10-02, `http://` responde 301 hacia `https://` en el dominio, en `www` y en `version.json`. hstspreload.org lo da por elegible (sin errores); enviarlo es opcional. |
| H-02 | ✅ Resuelto | `ControlBloqueo` vuelve a cobrar la espera al leer los fallos guardados; la pantalla del candado escucha `esperaHasta`. Dos pruebas nuevas. |
| H-03 | ✅ Resuelto | `XlsxLector.leeAcotado` corta al pasar el presupuesto mientras descomprime; tope de 2 000 partes. Prueba con una bomba de 65 MB. |
| H-04 | ✅ Resuelto | Con candado, el aviso no lleva nombre ni monto (`Recordatorios.textoDelAviso`); todos los avisos llevan una versión pública. Dos pruebas nuevas. |
| H-05 | 🟡 Parcial | Flujo `codeql.yml`. **Falta (fuera del repo):** activar secret scanning y push protection, y sacar el `.jks` y `keystore.properties` del árbol del proyecto. |
| H-06 | 🟡 Parcial | Todas las acciones fijadas por SHA, `distributionSha256Sum` en el wrapper y `environment: release` en el job que firma. **Falta:** mover los secretos al environment y limitarlo a tags `v*` (instrucciones en `docs/publicacion.md`); la verificación de dependencias de Gradle (`verification-metadata.xml`) queda pendiente, porque hay que generarla en Linux por los artefactos de `aapt2` de cada sistema. |
| H-07 | 🟡 Parcial | `.github/dependabot.yml` (Gradle, npm y acciones) y `dependencias.yml` para el grafo de Gradle. **Falta:** atender la primera ronda de PR, empezando por SQLCipher y `biometric`. |
| H-08 | ⏳ Pendiente | Es configuración de GitHub (rulesets para `main` y para los tags `v*`). |
| H-09 | 🟡 Parcial | `SECURITY.md` y la sección «Cuando algo sale mal» en `docs/publicacion.md`. **Falta:** activar *Private vulnerability reporting* y confirmar la copia del `.jks` fuera del equipo. |
| H-10 | ✅ Resuelto | *Acerca de → Licencias de terceros* con `res/raw/licencias_terceros.txt`. |
| H-11 | ✅ Resuelto | `docs/seguridad.md`, el sitio y *Acerca de* corregidos. |
| H-12 | ⏳ Pendiente | Agregar ktlint obliga a reformatear todo el código; conviene un cambio aparte. |
| H-13 | ⏳ Pendiente | Las pruebas de interfaz del PIN necesitan emulador para comprobarlas. |
| H-14 | ✅ Resuelto | `RegistroDeFallos` guarda el último fallo en el teléfono; *Acerca de* permite verlo, copiarlo o borrarlo. Excluido del respaldo. |
| H-15 | ✅ Resuelto | `Ooxml.criterioLiteral` fuerza la igualdad y escapa los comodines en todos los `SUMIFS` y `COUNTIFS`. |

## 7. Alcance y limitaciones

- **Revisado:** toda la configuración de build (`build.gradle.kts`, `libs.versions.toml`, ProGuard, wrapper), el manifiesto y las reglas de respaldo; todo `data/seguridad/`, `data/actualizaciones/`, `data/notify/Recordatorios.kt`, `data/db/` (base, migraciones y DAOs), el lector de `.xlsx` y fragmentos del exportador; `MainActivity`, `BloqueoPantalla` y el diálogo de PIN de `AjustesPantalla`; los 5 workflows; el sitio `web/`; README y `docs/seguridad.md`, `docs/actualizaciones.md` y `docs/publicacion.md`.
- **Muestreado:** el resto de pantallas de Compose y el importador (988 líneas) se revisaron por búsqueda de patrones (`innerHTML`, SQL crudo, `Log`, `PendingIntent`, intents), no línea por línea.
- **No revisado:** la ejecución de la app, el APK publicado (no se descompiló), la configuración de Cloudflare más allá de las cabeceras observadas y la cuenta de GitHub más allá de lo que expone la API.
- **Comandos ejecutados:** el script `inventario.sh` de la skill; `git log`, `git ls-files`, `git check-ignore`; `npm audit` en `web/`; `gh run list` y `gh api` (repositorio, protección de rama y Pages); `curl -I` contra el sitio y `hstspreload.org`. No se compiló ni se corrieron las pruebas: el último run de «Pruebas» en `main` (2026-09-20) y los semanales del 2026-09-28 están en verde.
- Esta auditoría es un análisis estático del repositorio más unas pocas comprobaciones en vivo. No sustituye una prueba de penetración ni asesoría legal.
