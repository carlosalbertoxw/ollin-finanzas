# Historial de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado según [SemVer](https://semver.org/lang/es/).

**Este archivo manda.** No es un resumen escrito después: es de donde salen las tres cosas que definen una publicación.

1. [`app/build.gradle.kts`](app/build.gradle.kts) lee de aquí el `versionName` y deriva el `versionCode`. No hay ningún número de versión escrito a mano en el build.
2. El flujo de [publicación](.github/workflows/publicacion.yml) toma la sección de la versión etiquetada y la usa como cuerpo de la release de GitHub.
3. El [sitio](docs/sitio.md) publica el `version.json` que consulta la app para avisar de una actualización.

Por eso un tag `v1.2.0` sin su `## [1.2.0]` aquí arriba **falla antes de compilar nada**. Es a propósito: una versión sin notas es una versión que nadie sabe si le conviene instalar.

Lo que todavía no se publica se va acumulando bajo `## [Sin publicar]`; al etiquetar, esa sección se renombra con el número y la fecha.

Los enlaces van con dirección completa: el mismo texto se lee en GitHub, en el sitio y en el cuerpo de la release, y una ruta relativa solo funcionaría en uno de los tres.

## [Sin publicar]

## [1.0.3] - 2026-08-31

### Añadido

- **Recordatorio de respaldo.** Un aviso cada siete días si no has exportado tu libro, y otro cuando aparece una versión nueva — que es justo cuando conviene tener una copia. Los dos abren la pantalla de Archivo. La semana se cuenta desde tu último respaldo, así que exportar calla el aviso; el de versión nueva se manda una sola vez por versión. Se apaga en `Ajustes → Respaldo`.

- Una prueba de actualización sobre emulador: instala la versión de la última etiqueta, la abre para que escriba sus preferencias, instala la nueva encima sin desinstalar y comprueba que sigue abriéndose. Es la que habría atrapado el fallo de la 1.0.1. Vive en `actualizacion.yml`. Por ahora informa sin bloquear: al ponerla a detener publicaciones dio tres falsos negativos seguidos contra una versión que abre bien en un teléfono real, y una puerta que detiene releases buenas se acaba ignorando. Vuelve a bloquear en cuanto se le vea pasar contra una versión conocida buena.

## [1.0.2] - 2026-08-31

### Arreglado

- **La app se cerraba al abrirla, después de actualizar a la 1.0.1.** La 1.0.0 guardaba la versión disponible como entero bajo la clave `version_publicada` y la 1.0.1 pidió ese mismo nombre como texto. DataStore guarda el tipo junto al valor, así que la lectura lanzaba `ClassCastException` dentro del flujo que alimenta el arranque, y eso cierra el proceso. Solo pasaba al instalar encima de la versión anterior: una instalación nueva no tiene nada guardado y por eso ni las pruebas ni un teléfono limpio lo veían. **No se pierde nada de lo capturado**: la 1.0.2 lee lo que dejó la 1.0.0 y sigue adelante.

### Cambiado

- Las lecturas de preferencias comprueban el tipo en tiempo de ejecución y tratan como ausente lo que no cuadre, así que un valor heredado no puede volver a cerrar la app. La regla —una clave no cambia de tipo nunca— y su prueba de actualización quedan en [modelo de datos](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/docs/modelo-de-datos.md).
- El trabajo de arranque —sembrar el catálogo, programar el aviso, buscar versión nueva— ya no puede tumbar la app: falla, se anota en el log y la app abre igual.

## [1.0.1] - 2026-08-31

### Cambiado

- **La app consulta `github.io` y no el dominio propio.** La dirección queda compilada dentro de cada APK y no se puede corregir en los que ya están instalados: `carlosalbertoxw.github.io` dura lo que el repositorio, mientras que un dominio se renueva cada año y se puede perder. El comprobador sigue **un** salto de redirección, y solo si el destino también es `https`.
- El repositorio adopta las convenciones de [Ollin Actividades](https://github.com/carlosalbertoxw/ollin-actividades): la versión sale de este archivo, la firma se toma de variables de entorno, los flujos se llaman `pruebas`, `publicacion` y `sitio`, y el sitio le pregunta a GitHub por la release en vez de dar por hecho lo que se compiló.

## [1.0.0] - 2026-08-30

Primera versión pública.

### Añadido

- **El libro.** Entradas, salidas, transferencias y saldos de apertura, con cuentas y categorías de listas cerradas. Los importes viven como centavos enteros, así que un saldo en cero es exactamente cero y las conciliaciones cuadran.
- **Transferencias en una captura.** Mover dinero entre tus cuentas escribe los dos renglones ligados por un mismo grupo. Borrar uno borra el otro: nunca queda media transferencia.
- **Patrimonio aparte del consumo.** Comprar un terreno o cripto es trasladar patrimonio, no gastarlo, y los tableros lo cuentan por separado. Por eso la tasa de ahorro no se desploma el mes que compraste algo grande.
- **Compromisos.** Mensualidades, suscripciones, la renta, el seguro anual, con periodicidad de semanal a anual. El plan solo avanza cuando lo decides —cumplir o descartar— y las dos se deshacen devolviendo el plan justo a donde estaba, incluso uno que cae en día 31.
- **Aviso diario de lo que vence**, a la hora que elijas en Ajustes.
- **Salud de los datos.** Nueve revisiones que corren en continuo: tipos que contradicen al signo del importe, transferencias sin pareja, saldos iniciales duplicados, movimientos sin categoría, medios incoherentes y descripciones casi idénticas. Tres se reparan con un botón, dentro de una sola transacción y sin tocar nunca el importe.
- **Presupuesto y analítica.** Meta contra realidad por categoría y mes, con las metas copiables al mes siguiente, y tendencia mensual.
- **Exportación e importación en Excel.** Un `.xlsx` con fórmulas vivas (`SUMIFS`), escrito y leído sin dependencias externas. Lo que sale puede volver a entrar, catálogos incluidos, y la importación es atómica: o entra todo o no entra nada.
- **Base cifrada.** AES-256 con SQLCipher y la frase envuelta en el Keystore de Android. No hay camino sin cifrar.
- **Bloqueo opcional.** Con la credencial del teléfono (patrón, PIN, huella) o con un PIN propio de Ollin, con espera creciente ante los intentos fallidos.
- **Aviso de actualizaciones.** Ollin consulta una vez al día si hay una versión más nueva publicada y lo enseña en *Acerca de*. Se apaga en Ajustes. Ver [seguridad y privacidad](https://github.com/carlosalbertoxw/ollin-finanzas/blob/main/docs/seguridad.md).
- **Sitio de descarga** en GitHub Pages, con el APK firmado, su huella y las instrucciones de instalación fuera de la tienda.

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/carlosalbertoxw/ollin-finanzas/releases/tag/v1.0.0
