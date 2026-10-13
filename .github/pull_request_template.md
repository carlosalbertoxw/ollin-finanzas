## Qué cambia y por qué

<!-- Lo que nota quien usa la app, y el motivo. Si cierra un issue: «Cierra #123». -->

## Antes de pedir revisión

Lo que exige [CONTRIBUTING](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/CONTRIBUTING.md). Tacha lo que no aplique en vez de borrarlo.

- [ ] El `CHANGELOG.md` lo cuenta bajo `## [Sin publicar]`, si se nota en la app.
- [ ] La documentación cambia en este mismo PR, si este PR la vuelve obsoleta.
- [ ] Cada corrección trae la prueba que habría fallado antes del arreglo.
- [ ] Un cambio de esquema trae su `Migration`, su esquema exportado y su prueba.
- [ ] Una dependencia nueva o actualizada trae su huella en `gradle/verification-metadata.xml`.
- [ ] `./gradlew spotlessCheck testDebugUnitTest lintDebug` pasa en local.
- [ ] Si se usó un asistente de IA, el commit lo declara con `Co-Authored-By` y aquí se dice. Quien firma puede explicar cada línea.

## Lo que se mira con más cuidado

Marca lo que toque. Un error aquí llega a todos los teléfonos y no se arregla desde fuera:

- [ ] `.github/workflows/` o `.github/scripts/`: la cadena que firma y anuncia las versiones.
- [ ] `app/proguard-rules.pro` o dependencias: lo que R8 deja en el APK de release.
- [ ] `data/seguridad/`, `ui/seguridad/` o el modo de bloqueo en `Ajustes`: el candado y la llave de la base.
- [ ] `data/excel/XlsxLector.kt` o `ImportadorExcel.kt`: lo único que lee archivos que vienen de fuera.
- [ ] `domain/model/Dinero.kt` o cualquier cuenta con montos: todo va en centavos, nunca en `Double`.
- [ ] El `version.json` o `ComprobadorActualizaciones`: el contrato con las instalaciones que ya existen.
