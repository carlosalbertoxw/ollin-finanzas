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

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/carlosalbertoxw/ollin-finanzas/releases/tag/v1.0.0
