# Auditoría del proyecto: ollin-finanzas

**Fecha:** 2026-10-06 · **Commit auditado:** `230a24c` (main) · **Auditor:** Claude (skill auditoria-proyecto-software)

## 1. Resumen ejecutivo

Ollin Finanzas es una app Android de finanzas personales (Kotlin, Jetpack Compose, Room y SQLCipher) que funciona solo en el teléfono: no tiene servidor ni cuentas, y la única salida a la red es una consulta diaria a un `version.json` estático. Se distribuye como APK firmado fuera de Google Play, con un sitio de descarga en Vite publicado en GitHub Pages. La exposición es baja (los datos no salen del teléfono), pero los datos son financieros y el canal de distribución es propio. Por eso las dos superficies que más importan son los archivos `.xlsx` que entran desde fuera y la cadena de publicación.

El proyecto es muy maduro para su tamaño. La base está cifrada con una llave del Keystore, el desbloqueo está atado criptográficamente al Keystore y las acciones de CI están fijadas por SHA. La publicación comprueba la huella del certificado, hay una prueba que instala la versión nueva sobre la anterior y las decisiones de diseño están documentadas. No encontré hallazgos críticos ni altos. Los tres hallazgos medios tocan justo esas dos superficies.

**Calificación global:** 87.5 % — Sólido
**Hallazgos:** 0 críticos · 0 altos · 3 medios · 9 bajos · 3 informativos
**Controles no verificables desde el código:** 7 de 79

**Prioridades principales**
1. **[H-01]** Un `.xlsx` manipulado puede agotar la memoria al importarse: los índices de fila y columna no tienen tope.
2. **[H-02]** El enlace de "versión nueva" acepta cualquier host `https`. Si el dominio propio se pierde, el aviso de actualización se convierte en un vector de phishing.
3. **[H-03]** Es probable que los secretos de firma estén como secretos del repositorio y no del environment `release`. El propio flujo indica guardarlos así.
4. **[H-04]** El diálogo de PIN de Ajustes no pasa por el freno de intentos de `ControlBloqueo`.

## 2. Resultados por área

| # | Área | Cumple | Parcial | No cumple | N/A | No verif. | Puntaje |
|---|------|-------:|--------:|----------:|----:|----------:|--------:|
| 01 | Fundamentos del proyecto | 1 | 0 | 0 | 1 | 1 | 100 % |
| 02 | Diseño y arquitectura | 7 | 2 | 0 | 0 | 2 | 89 % |
| 03 | Documentación | 7 | 1 | 0 | 1 | 0 | 94 % |
| 04 | Pruebas y calidad | 2 | 2 | 0 | 0 | 0 | 75 % |
| 05 | Automatización y despliegue | 4 | 0 | 0 | 0 | 0 | 100 % |
| 06 | Observabilidad y operación | 1 | 0 | 0 | 5 | 0 | 100 % |
| 07 | Secretos y configuración | 2 | 1 | 0 | 0 | 0 | 83 % |
| 08 | Autenticación y sesiones | 2 | 2 | 0 | 1 | 1 | 75 % |
| 09 | Base de datos y autorización | 0 | 0 | 0 | 5 | 0 | N/A |
| 10 | Validación y protección de datos | 5 | 1 | 0 | 0 | 0 | 92 % |
| 11 | Archivos y APIs | 1 | 1 | 0 | 2 | 0 | 75 % |
| 12 | Seguridad web | 1 | 1 | 0 | 2 | 1 | 75 % |
| 13 | Dependencias y vigilancia | 3 | 2 | 0 | 1 | 2 | 80 % |
| 14 | Cumplimiento y aspectos legales | 4 | 0 | 0 | 1 | 0 | 100 % |

La calificación global es el promedio de las 13 áreas evaluables. El área 09 queda fuera porque no hay servidor ni varios usuarios.

<details>
<summary>Detalle de estado por control</summary>

| Control | Estado | Nota breve |
|---------|--------|------------|
| FUN-01 | ✅ Cumple | Mensajes de commit descriptivos que explican el porqué; sin binarios ni dependencias versionadas. |
| FUN-02 | ❓ No verificable | Los cambios entran por PR (#1, #9, #12, #13), pero hay un solo autor y no hay CODEOWNERS. La protección de rama está en GitHub. |
| FUN-03 | ➖ N/A | App Android: el entorno se reproduce con el Gradle wrapper, que tiene `distributionSha256Sum`. |
| ARQ-01 | ❓ No verificable | No hay objetivos con cifras (tiempo de arranque, tamaño del APK, filas soportadas). |
| ARQ-02 | ❓ No verificable | Las decisiones se documentan después (README, `docs/`), sin propuestas previas a la implementación. |
| ARQ-03 | ⚠️ Parcial | `docs/seguridad.md` describe los activos y los límites (root), pero no enumera atacantes ni puntos de entrada. Ver H-12. |
| ARQ-04 | ✅ Cumple | Dependencias mínimas y justificadas (sin POI, sin OkHttp, sin Hilt), con el motivo escrito. |
| ARQ-05 | ✅ Cumple | Un solo módulo y un contenedor de dependencias hecho a mano. |
| ARQ-06 | ✅ Cumple | Capas `data`/`domain`/`ui`; la UI no toca DAOs. Usar las entidades como dominio es una decisión documentada. |
| ARQ-07 | ✅ Cumple | El repositorio concentra la escritura y la descarga es inyectable para pruebas. |
| ARQ-08 | ⚠️ Parcial | Archivos de 700 a 990 líneas y sin reglas de complejidad. Ver H-08. |
| ARQ-09 | ✅ Cumple | Según la muestra revisada: las sumas de los filtros se calculan en SQL y no se vio N+1. |
| ARQ-10 | ✅ Cumple | Timeouts de 10 s, tope de 64 K en la respuesta, un solo salto de redirección y solo `https`. |
| ARQ-11 | ✅ Cumple | El contrato de `version.json` está documentado en `docs/actualizaciones.md`. |
| DOC-01 | ✅ Cumple | El README explica qué es, para quién y qué no hace. |
| DOC-02 | ✅ Cumple | JDK, SDK y comandos documentados; no hay variables de entorno de app. |
| DOC-03 | ✅ Cumple | Árbol de carpetas en el README y `docs/arquitectura.md`. |
| DOC-04 | ✅ Cumple | Sección "Decisiones que no son las de default, y por qué" en el README. |
| DOC-05 | ➖ N/A | No hay API. |
| DOC-06 | ✅ Cumple | `docs/desarrollo.md` cubre convenciones y flujo; proyecto de un solo autor. |
| DOC-07 | ✅ Cumple | `docs/publicacion.md` documenta cómo retirar una release mala y qué hacer si la llave se compromete. |
| DOC-08 | ✅ Cumple | CHANGELOG semántico del que salen la versión y las notas; 6 tags. |
| DOC-09 | ⚠️ Parcial | Hay cifras y textos desfasados. Ver H-10. |
| PRU-01 | ✅ Cumple | 230 pruebas unitarias en la JVM, incluidas las de seguridad del lector XLSX y de `ControlBloqueo`. |
| PRU-02 | ✅ Cumple | Base en memoria con Robolectric, round-trip de Excel y prueba de actualización sobre la versión anterior en emulador. |
| PRU-03 | ⚠️ Parcial | 13 pruebas instrumentadas, semanales y no bloqueantes; no cubren el candado ni importar/exportar. Ver H-09. |
| PRU-04 | ⚠️ Parcial | Android Lint corre en CI; no hay ktlint/detekt ni formateador. Ver H-08. |
| CI-01 | ✅ Cumple | Pruebas, Lint, R8, CodeQL y publicación firmada automatizadas. |
| CI-02 | ✅ Cumple | Los `\|\| true` están justificados y no están en pasos de prueba. |
| CI-03 | ✅ Cumple | Esquemas de Room exportados, `EsquemaDeBaseTest` y sin `fallbackToDestructiveMigration`. |
| CI-04 | ✅ Cumple | Releases etiquetadas y un procedimiento para retirar una versión; Android no permite volver a una anterior. |
| OBS-01…05 | ➖ N/A | App móvil sin servidor. No hay telemetría a propósito; el último fallo se guarda en el teléfono (`RegistroDeFallos`). |
| OBS-06 | ✅ Cumple | El respaldo es la exportación `.xlsx`, con recordatorio semanal. La restauración se prueba con `ExcelRoundTripTest`. |
| SEC-01 | ✅ Cumple | No hay secretos en el código; `keystore.properties` y `*.jks` están en `.gitignore`. |
| SEC-02 | ✅ Cumple | Ni `.jks` ni `keystore.properties` aparecen en el historial. |
| SEC-03 | ⚠️ Parcial | Firma por variables de entorno en CI, pero los secretos probablemente están a nivel de repositorio. Ver H-03. |
| AUT-01 | ✅ Cumple | El candado arranca cerrado, sobrevive a la recreación de la actividad y el desbloqueo exige un cifrador del Keystore. |
| AUT-02 | ⚠️ Parcial | PBKDF2-HMAC-SHA256 con 120 000 iteraciones. Ver H-05. |
| AUT-03 | ⚠️ Parcial | La pantalla de bloqueo tiene espera exponencial persistente; el diálogo de Ajustes no. Ver H-04. |
| AUT-04 | ➖ N/A | No hay cookies. |
| AUT-05 | ✅ Cumple | Se vuelve a bloquear tras 60 s en segundo plano, con reloj monótono. |
| AUT-06 | ❓ No verificable | Aplica a la cuenta de GitHub que publica las releases. |
| BD-01…05 | ➖ N/A | SQLite local de un solo usuario; sin servidor ni varios inquilinos. |
| VAL-01 | ✅ Cumple | El importador normaliza tipos, signos, transferencias y catálogos. |
| VAL-02 | ✅ Cumple | Compose no interpreta HTML; el sitio no usa `innerHTML`. |
| VAL-03 | ✅ Cumple | Room con consultas parametrizadas; sin SQL crudo. |
| VAL-04 | ⚠️ Parcial | Las URLs que llegan en `version.json` solo se validan por esquema, no por host. Ver H-02. |
| VAL-05 | ✅ Cumple | SQLCipher AES-256 con la frase envuelta por el Keystore. |
| VAL-06 | ✅ Cumple | Sin identificadores del dispositivo; el informe de fallos tiene tope y queda fuera del respaldo. |
| API-01 | ⚠️ Parcial | Tope de 64 MB, 2 000 partes y rechazo de DOCTYPE, pero sin tope de filas ni columnas. Ver H-01. |
| API-02 | ✅ Cumple | Selector del sistema (SAF); la app no inventa rutas ni nombres. |
| API-03, API-04 | ➖ N/A | No hay API. |
| WEB-01 | ❓ No verificable | "Enforce HTTPS" de Pages con dominio propio se configura en GitHub. La app sí exige `https`. |
| WEB-02 | ⚠️ Parcial | El sitio no declara CSP. Ver H-11. |
| WEB-03, WEB-04 | ➖ N/A | Sitio estático sin sesión. |
| WEB-05 | ✅ Cumple | `explica()` oculta los internos al usuario; los logs no llevan datos del libro. |
| DEP-01 | ⚠️ Parcial | Dependabot y versiones mínimas forzadas; `npm audit` reporta 1 alta. Ver H-06. |
| DEP-02 | ✅ Cumple | `package-lock.json` con `npm ci`, catálogo de versiones exactas, wrapper con checksum y acciones fijadas por SHA. |
| DEP-03 | ⚠️ Parcial | Dependabot semanal y envío del grafo de Gradle; no hay SBOM. Ver H-07. |
| DEP-04 | ✅ Cumple | CodeQL en push, PR y cada semana. |
| DEP-05 | ➖ N/A | Un solo usuario y sin servidor. |
| DEP-06, DEP-07 | ❓ No verificable | Ver preguntas. |
| DEP-08 | ✅ Cumple | `SECURITY.md` con reporte privado y procedimiento ante una llave comprometida. |
| LEG-01 | ✅ Cumple | MIT, con `licencias_terceros.txt` visible en la app. |
| LEG-02 | ➖ N/A | No se recogen datos personales fuera del dispositivo. |
| LEG-03 | ✅ Cumple | La sección de privacidad del README y del sitio coincide con el código: una sola petición, sin datos. |
| LEG-04 | ✅ Cumple | Sin SDK de terceros; solo GitHub como hosting. |
| LEG-05 | ✅ Cumple | LICENSE y un único autor. |

</details>

## 3. Hallazgos

### [H-01] Un `.xlsx` manipulado puede agotar la memoria al importarse — 🟡 Media
- **Control:** API-01 · Subidas de archivos restringidas por tamaño y contenido
- **Evidencia:**
  - `app/src/main/java/com/carlosalbertoxw/ollin/finanzas/data/excel/XlsxLector.kt:312` toma el número de fila del atributo `r` sin tope (`<row r="2000000000">`).
  - En `:301` se rellenan los huecos con `while (filas.size < numeroFilaActual - 1) filas.add(emptyList())`, así que una sola fila con `r` enorme crea cientos de millones de elementos.
  - En `:316` y `:302` la columna sale de `Ooxml.indiceColumna` sin tope, y `(1..maxColFila).map { … }` crea una celda vacía por cada columna hasta la mayor. Unas pocas filas con `r="XFD1"` producen 16 384 objetos cada una.
  - `Ooxml.kt:32-39` (`indiceColumna`) además desborda `Int` con referencias largas (`AAAAAAAAA1`).
  - Las defensas actuales (64 MB descomprimidos, 2 000 partes y rechazo de `DOCTYPE`) acotan los bytes, no la cantidad de objetos que salen de ellos.
- **Riesgo:** un archivo de pocos KB presentado como "respaldo" cierra la app con `OutOfMemoryError` al importarlo. El libro no se corrompe, porque `XlsxLector.lee` corre antes de `withTransaction` (`ImportadorExcel.kt:183-185`), pero el error no se puede manejar y la persona pierde la confianza en la importación.
- **Recomendación:**
  1. Rechazar `r` de fila mayor que 1 048 576 y columnas mayores que 16 384, que son los límites de Excel. En `indiceColumna`, cortar si hay más de 3 letras.
  2. Llevar un presupuesto de celdas materializadas (por ejemplo, filas × columnas ≤ 5 000 000) y lanzar `ArchivoInvalido` con el mismo mensaje de `demasiadoGrande()`.
  3. Agregar casos a `XlsxLectorSeguridadTest`: fila con `r` gigante, celda `XFD`, referencia de 10 letras.
- **Esfuerzo estimado:** Bajo

### [H-02] El enlace de "versión nueva" acepta cualquier host `https` — 🟡 Media
- **Control:** VAL-04 · URLs de origen externo validadas contra una lista permitida
- **Evidencia:**
  - `ComprobadorActualizaciones.kt:128-130` acepta `apk` o `sitio` con tal de que empiecen por `https://`.
  - `:243` (`siguienteSalto`) sigue la redirección a cualquier host `https`.
  - `app/build.gradle.kts` compila la URL de `github.io`, que según `docs/actualizaciones.md:61` redirige a `carlosalbertoxw.com`.
  - `AcercaDePantalla.kt:205` abre esa URL con `ACTION_VIEW`.
- **Riesgo:** el comentario del build ya reconoce que un dominio se puede perder, pero la app igual sigue el salto hacia él y confía en lo que responda. Si `carlosalbertoxw.com` vence o se secuestra su DNS mientras Pages siga redirigiendo, un tercero sirve un `version.json` con una versión inventada. Cada instalación muestra entonces un botón de "versión nueva" que lleva a su APK. Android rechazaría instalarlo encima por la firma distinta, pero quien desinstale para "arreglarlo" queda expuesto, y el aviso sale de la propia app, que es la fuente en la que más se confía.
- **Recomendación:**
  1. Lista permitida de prefijos para `apk`: `https://github.com/carlosalbertoxw/ollin-finanzas/releases/`. Para `sitio`: los dos orígenes conocidos.
  2. Limitar el destino de la redirección a `carlosalbertoxw.github.io` y `carlosalbertoxw.com`.
  3. Mostrar en *Acerca de*, junto al botón, la huella SHA-256 del certificado compilada en la app, para que se pueda comparar.
  4. A mediano plazo, firmar `version.json` (Ed25519 con la clave pública dentro del APK) en `sitio.yml`, para que el canal deje de depender del DNS.
- **Esfuerzo estimado:** Bajo (1–3), Medio (4)

### [H-03] Secretos de firma probablemente al alcance de cualquier flujo — 🟡 Media
- **Control:** SEC-03 · Gestión de secretos
- **Evidencia:** `.github/workflows/publicacion.yml:160` declara `environment: release`, pero el comentario anterior admite que "mientras sigan siendo del repositorio, esta línea no cambia nada". El mensaje de error de `:204-207` le indica a quien publica que los cargue "como secretos del repositorio y no de un entorno". Eso contradice `docs/publicacion.md:34-37`, que recomienda el environment limitado a `v*`. La configuración real no se puede ver desde el código.
- **Riesgo:** con secretos de repositorio, cualquier workflow de cualquier rama puede leer el almacén y sus contraseñas, y también un paso comprometido de otro flujo. Una llave de firma filtrada permite publicar actualizaciones falsas que Android instala encima sin avisar, y rotarla exige el linaje de APK Signature v3 o la reinstalación de todos.
- **Recomendación:**
  1. Mover los cuatro secretos al environment `release`, con regla de tags `v*` y, si es posible, revisión obligatoria. Después borrarlos del repositorio.
  2. Corregir el mensaje de `publicacion.yml:204-207` para que indique el environment.
  3. Documentar en `docs/publicacion.md` cómo se rotaría la llave con `apksigner rotate` (el v3 ya está activo), para no tener que improvisarlo.
- **Esfuerzo estimado:** Bajo

### [H-04] El diálogo de PIN en Ajustes no pasa por el freno de intentos — 🔵 Baja
- **Control:** AUT-03 · Límite de intentos de autenticación
- **Evidencia:** `ui/screens/AjustesPantalla.kt:619` llama a `ClavePin.coincide` directamente. En `BloqueoPantalla.kt:138-148` el mismo cálculo pasa por `segundosDeEspera()` y `registraFalloDePin()`.
- **Riesgo:** quien encuentre la app abierta, dentro del minuto de gracia, puede probar PINs sin espera hasta dar con él. Con un PIN de 4 dígitos son 10 000 intentos, y ese PIN puede ser el mismo que la persona usa en otros sitios. Es bajo porque esa persona ya ve el libro.
- **Recomendación:** pasar el diálogo por `ControlBloqueo` (`segundosDeEspera`, `registraFalloDePin`, `registraAciertoDePin`) y mostrar la misma cuenta regresiva.
- **Esfuerzo estimado:** Bajo

### [H-05] PBKDF2 con 120 000 iteraciones para el PIN — 🔵 Baja
- **Control:** AUT-02 · Derivación de contraseñas
- **Evidencia:** `data/seguridad/ClavePin.kt:22`.
- **Riesgo:** OWASP recomienda 600 000 iteraciones para PBKDF2-HMAC-SHA256. En la práctica pesa más el espacio de 10 000 PINs que el número de iteraciones: quien tenga el archivo de preferencias (requiere root, porque está fuera del respaldo) lo recorre en minutos con cualquier configuración.
- **Recomendación:** la mejora real es atar la verificación al hardware. Calcular un HMAC con una llave del Keystore sobre la huella PBKDF2, para que el ataque fuera de línea exija el teléfono. Si se suben las iteraciones, versionar la huella y recalcularla en el siguiente acierto.
- **Esfuerzo estimado:** Medio

### [H-06] Vulnerabilidad alta conocida en una dependencia de desarrollo del sitio — 🔵 Baja
- **Control:** DEP-01 · Escaneo de vulnerabilidades
- **Evidencia:** `npm audit` en `web/` reporta `source-map-js@1.2.1` (vía `vite` → `postcss`), GHSA-68fv-2mgg-jv7q, severidad alta. Además, el `node_modules` local tiene `vite@8.2.2` aunque el lockfile fija `8.3.1`.
- **Riesgo:** bajo. Solo corre al compilar el sitio y no viaja al APK ni a la página publicada.
- **Recomendación:** `npm audit fix` o aceptar el PR de Dependabot. Agregar `npm audit --audit-level=high` al job `sitio` de `pruebas.yml`, y correr `npm ci` en local para alinearse con el lockfile.
- **Esfuerzo estimado:** Bajo

### [H-07] Sin SBOM de lo que se publica — 🔵 Baja
- **Control:** DEP-03 · Inventario de dependencias
- **Evidencia:** `dependencias.yml` envía el grafo a GitHub, pero la release no adjunta ningún SBOM (`publicacion.yml`, paso "Preparar la entrega").
- **Riesgo:** ante una alerta futura (por ejemplo en SQLCipher) no hay un registro inmutable de qué versión exacta llevaba cada APK publicado.
- **Recomendación:** generar un CycloneDX del `releaseRuntimeClasspath` (plugin `org.cyclonedx.bom` o `anchore/sbom-action`) y adjuntarlo a la release junto a `checksums.txt`.
- **Esfuerzo estimado:** Bajo

### [H-08] Sin formateador ni reglas de complejidad para Kotlin — 🔵 Baja
- **Control:** PRU-04 / ARQ-08 · Linter y código mantenible
- **Evidencia:** solo hay `.editorconfig` y `lintDebug`. Los archivos más largos son `ImportadorExcel.kt` (988 líneas), `CapturaPantalla.kt` (775), `ExportadorExcel.kt` (726), `AjustesPantalla.kt` (684) y `CompromisosPantalla.kt` (676).
- **Riesgo:** las pantallas de 700 líneas mezclan diálogos, estado y lógica de verificación. H-04 es justo eso: una regla de seguridad duplicada a medias dentro de una pantalla.
- **Recomendación:** agregar ktlint (o Spotless con ktlint) y detekt con `LongMethod`/`CyclomaticComplexMethod`, empezando con baseline para no bloquear. Extraer los diálogos de PIN de `AjustesPantalla` a `ui/seguridad/`.
- **Esfuerzo estimado:** Medio

### [H-09] Las pruebas de extremo a extremo no cubren el candado ni importar/exportar — 🔵 Baja
- **Control:** PRU-03 · Pruebas end-to-end de flujos críticos
- **Evidencia:** `app/src/androidTest` solo tiene `AvisoDeRespaldoEnTableroTest`, `ComponentesComunesTest` y `NavegacionTest` (13 pruebas), y `pruebas-instrumentadas.yml` no bloquea.
- **Riesgo:** el desbloqueo con `BiometricPrompt` y Keystore, y el selector de archivos (que ya se rompió una vez por `fragment`, según el README), solo se prueban a mano.
- **Recomendación:** agregar una prueba instrumentada del candado por PIN (fallos, espera y acierto) y otra de exportar e importar contra un `DocumentsProvider` de prueba.
- **Esfuerzo estimado:** Medio

### [H-10] Documentación desfasada en tres puntos — 🔵 Baja
- **Control:** DOC-09 · Documentación tratada como código
- **Evidencia:**
  - `docs/desarrollo.md:123` dice 208 pruebas unitarias y 11 de interfaz; hoy son 230 y 13.
  - `docs/publicacion.md:147` dice "Ollin Finanzas y Ollin Finanzas son dos aplicaciones distintas"; por el comentario de `app/build.gradle.kts`, la segunda es Ollin Actividades.
  - `publicacion.yml:204-207` contradice a `docs/publicacion.md:34-37` (ver H-03).
- **Recomendación:** corregir los tres puntos. Para las cifras, conviene no escribirlas o generarlas.
- **Esfuerzo estimado:** Bajo

### [H-11] El sitio de descarga no declara CSP — 🔵 Baja
- **Control:** WEB-02 · Cabeceras de seguridad
- **Evidencia:** `web/index.html` no tiene `Content-Security-Policy` ni `referrer`. GitHub Pages no permite cabeceras propias.
- **Riesgo:** bajo; es un sitio estático sin entradas. Pero es la página de la que la gente baja el APK, y una CSP limita el daño de un script inyectado en la cadena de build.
- **Recomendación:** `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; img-src 'self'; style-src 'self'; script-src 'self'; base-uri 'none'; form-action 'none'">` y `<meta name="referrer" content="no-referrer">`.
- **Esfuerzo estimado:** Bajo

### [H-12] El modelado de amenazas existe, pero de forma implícita — 🔵 Baja
- **Control:** ARQ-03 · Modelado de amenazas
- **Evidencia:** `docs/seguridad.md` describe muy bien qué se protege y qué no (root, Keystore, respaldo), pero no enumera los puntos de entrada ni los adversarios.
- **Riesgo:** H-01 y H-02 están en dos puntos de entrada (el `.xlsx` ajeno y el canal de actualizaciones) que una lista explícita habría puesto en evidencia.
- **Recomendación:** agregar a `docs/seguridad.md` una tabla de entradas: teléfono robado o prestado, `.xlsx` de un tercero, `version.json` y DNS, cuenta de GitHub y secretos de CI, dependencias. Indicar para cada una la defensa y el riesgo aceptado.
- **Esfuerzo estimado:** Bajo

### [I-01] El almacén de firma vive dentro de la carpeta del proyecto — ⚪ Informativa
- **Control:** SEC-01
- **Evidencia:** `ollin-finanzas-release.jks` y `keystore.properties` están en la raíz de la copia de trabajo. Están bien ignorados (`.gitignore:16,20`) y nunca entraron al historial.
- **Nota:** el riesgo no es Git sino copiar la carpeta: un zip para compartir, la sincronización de `Documentos` con OneDrive, una herramienta que suba el árbol completo. Conviene mover el `.jks` fuera del repositorio, apuntar `storeFile` a esa ruta y tener una copia fuera de línea. Perderlo impide actualizar a todos los usuarios.

### [I-02] `versionCode` con huecos de dos cifras — ⚪ Informativa
- **Evidencia:** `app/build.gradle.kts` calcula `mayor * 10_000 + menor * 100 + parche`.
- **Nota:** está documentado y es razonable. Una `1.100.0` daría `20000`, el mismo código que la `2.0.0`, y Android rechazaría instalar la segunda encima. Una validación en `publicacion.yml` (menor y parche < 100) haría explícito el límite.

### [I-03] Buenas prácticas destacadas — ⚪ Informativa
- Desbloqueo atado a un `CryptoObject` del Keystore, que neutraliza el bypass del callback con Frida.
- Espera exponencial persistente y reloj monótono en el candado.
- `FLAG_SECURE` mientras hay candado.
- La base, la llave, las preferencias y el log de fallos están fuera del respaldo y de la transferencia entre dispositivos.
- Lector XLSX con presupuesto de bytes por parte (zip bomb) y rechazo de `DOCTYPE`, con sus pruebas.
- Acciones de GitHub fijadas por SHA; el wrapper de Gradle con checksum.
- La publicación comprueba la huella del certificado contra la del README antes de crear la release.
- Prueba de actualización sobre la versión anterior, bloqueante.
- Versiones mínimas forzadas para dependencias transitivas vulnerables del build.
- Comentarios que explican el porqué de forma consistente.

## 4. Preguntas para el equipo

| Control | Pregunta |
|---------|----------|
| FUN-02 | ¿`main` tiene protección de rama con los checks de `Pruebas` como requeridos? ¿Se puede hacer push directo? |
| SEC-03 | ¿Los cuatro secretos `OLLIN_FINANZAS_*` están hoy en el repositorio o en el environment `release`? ¿El environment tiene regla de tags `v*`? |
| AUT-06 | ¿La cuenta de GitHub que publica las releases tiene 2FA con llave física o TOTP? ¿Y el registrador del dominio `carlosalbertoxw.com`? |
| WEB-01 | ¿Pages tiene activado "Enforce HTTPS" para el dominio propio? ¿El dominio tiene renovación automática y bloqueo de transferencia? |
| ARQ-01 | ¿Hay un volumen objetivo de movimientos (por ejemplo 50 000) con el que se haya medido la importación y el tablero? |
| ARQ-02 | ¿Los cambios grandes (por ejemplo la migración a AGP 9 o el desbloqueo por Keystore) se discuten en un issue antes del PR? |
| DEP-04 | ¿Están activos el secret scanning y la push protection de GitHub en el repositorio? |
| DEP-06 | ¿Quién recibe las alertas de Dependabot y CodeQL, y en qué plazo se atienden? |
| DEP-07 | ¿Se ha hecho alguna revisión externa o análisis dinámico (por ejemplo MobSF) del APK publicado? |
| I-01 | ¿Dónde está la copia fuera de línea del almacén de firma? ¿Se ha probado firmar con ella? |

## 5. Plan de remediación

**Inmediato (antes de la próxima release):**
- H-03: mover los secretos de firma al environment `release` y corregir el mensaje del flujo.
- H-01: poner tope a filas, columnas y celdas en `XlsxLector`, con sus pruebas.
- H-02: lista permitida de hosts para `apk`, `sitio` y redirecciones.

**Corto plazo (2 a 4 semanas):**
- H-04 y H-08: extraer los diálogos de PIN a `ui/seguridad/` y pasarlos por `ControlBloqueo`.
- H-06: `npm audit fix` y la auditoría de npm en CI.
- H-10 y H-12: correcciones de documentación y tabla de puntos de entrada en `docs/seguridad.md`.
- I-01: sacar el `.jks` de la carpeta del proyecto.

**Mediano plazo:**
- H-05: verificación del PIN atada al Keystore.
- H-07: SBOM adjunto a cada release.
- H-08: ktlint y detekt con baseline.
- H-09: pruebas instrumentadas del candado y de importar/exportar.
- H-11: CSP en el sitio.
- H-02 (4): firma de `version.json`.

## 6. Alcance y limitaciones

- **Revisado completo:**
  - `data/seguridad/`, `data/actualizaciones/`, `data/diagnostico/` y `MainActivity`.
  - `ui/seguridad/`, los flujos de PIN de `BloqueoPantalla` y `AjustesPantalla`, y `XlsxLector`.
  - Manifiesto, reglas de respaldo, `proguard-rules.pro`, los build de Gradle y el catálogo de versiones.
  - Los 7 workflows, `dependabot.yml`, README, `SECURITY.md`, `docs/seguridad.md` y `docs/publicacion.md`.
  - El sitio (`web/index.html`, `package.json`, `vite.config.js`).
- **Muestreado:** `FinanzasRepositorio` (importar/exportar, transacciones), `Ooxml` (escape XML e índices), `ImportadorExcel` (orden lectura/transacción), la estructura de capas de `ui/` y el inventario de pruebas.
- **No revisado:** la lógica de negocio de `RevisaCalidad`/`ReparaDatos`, las proyecciones SQL una por una, `ExportadorExcel` en detalle, la configuración de GitHub (protección de ramas, environments, secret scanning, Pages) y el APK publicado.
- **Comandos ejecutados:** el script `inventario.sh` de la skill, `git ls-files`, `git log` (historial de `*.jks` y `keystore.properties`, merges), `git check-ignore`, `npm audit` en `web/` (solo lectura) y búsquedas con `grep`.
- Esta auditoría es un análisis estático del repositorio y no sustituye pruebas de penetración ni asesoría legal.

## 7. Seguimiento de correcciones

Corregido en la rama `auditoria-correcciones` (sin commit) el 2026-10-06.

| Hallazgo | Estado | Qué se hizo |
|---|---|---|
| H-01 | ✅ Resuelto | `XlsxLector` rechaza filas más allá de 1 048 576 y columnas más allá de XFD, `Ooxml.indiceColumna` satura en vez de desbordar, y el libro tiene un presupuesto de 3 millones de celdas. Los huecos se rellenan con una sola instancia vacía. Hay 5 pruebas nuevas en `XlsxLectorSeguridadTest`. |
| H-02 | ✅ Resuelto | `DestinosPermitidos`: el APK solo puede venir de `github.com/carlosalbertoxw/ollin-finanzas/releases/`, y el sitio y las redirecciones solo de sus dos domicilios. Se compara la URI desarmada (rechaza `..`, `%2e`, usuario, puerto, consulta). Lo guardado por versiones anteriores pasa por la misma lista. Hay 5 pruebas nuevas. No se firmó `version.json` (decisión del equipo). |
| H-03 | ⚠️ Parcial | El mensaje y el comentario de `publicacion.yml` y `docs/publicacion.md` ya piden el environment `release`, y se documentó la rotación con el linaje v3. **Falta mover los secretos en GitHub**, que solo puede hacer el dueño del repositorio. |
| H-04 | ✅ Resuelto | Todo PIN pasa por `ControlBloqueo.intentaPin` (con `Mutex`), también el diálogo de Ajustes, que se movió a `ui/seguridad/DialogosPin.kt` con su cuenta regresiva. |
| H-05 | ✅ Resuelto | La huella se sella con HMAC-SHA256 y una llave del Keystore (`LlaveDelPin`, prefijo `ks1:`). Las huellas viejas se migran solas en el siguiente acierto. |
| H-06 | ✅ Resuelto | `npm audit fix` (`source-map-js` 1.2.2) y `npm audit --audit-level=high` en `pruebas.yml`. |
| H-07 | ✅ Resuelto | Plugin CycloneDX 3.4.1 limitado a `releaseRuntimeClasspath`. El SBOM se adjunta a cada release y entra en `checksums.txt`. |
| H-08 | ⚠️ Parcial | ktlint 1.8.0 en CI, con el estilo IntelliJ, las reglas de acomodo apagadas con su motivo y un baseline de 280 entradas, casi todas líneas largas. Encontró un bloque mal sangrado en `TableroPantalla`, que ya se corrigió. Los diálogos de PIN salieron de `AjustesPantalla`. Siguen pendientes las pantallas y el importador largos, y no se agregó detekt (decisión del equipo). |
| H-09 | ⚠️ Parcial | Se escribieron `CandadoPinTest`, `LlaveDelPinTest` y `ExportarImportarTest`. Compilan, pero **no se han ejecutado**: no hay emulador local. Las correrá `pruebas-instrumentadas.yml`. |
| H-10 | ✅ Resuelto | Sin conteos de pruebas escritos a mano, «Ollin Actividades» corregido en tres lugares y el mensaje del flujo alineado con la documentación. También se encontró y corrigió que `usesCleartextTraffic="false"` estaba documentado pero no declarado en el manifiesto. |
| H-11 | ✅ Resuelto | CSP y `referrer` inyectados en el build del sitio (no en `dev`). Verificado en el navegador: carga sin errores. |
| H-12 | ✅ Resuelto | Tabla «Por dónde podría entrar alguien» en `docs/seguridad.md`. |
| I-01 | ⏳ Pendiente | Mover el `.jks` fuera de la carpeta del proyecto le toca al dueño. |
| I-02 | ✅ Resuelto | `publicacion.yml` rechaza una versión con menor o parche mayor que 99. |
