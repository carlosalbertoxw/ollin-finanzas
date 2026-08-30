# Registro de cambios

Todo lo que cambia entre versiones, escrito para quien usa la app y no para quien lee los commits.

De aquí saca sus notas el flujo de release: al publicar `vX.Y.Z` busca la sección `[X.Y.Z]` y la pega en el lanzamiento de GitHub. **Si la sección no existe, la publicación se detiene** antes de compilar nada — así ninguna versión sale sin explicar qué trae.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones, [SemVer](https://semver.org/lang/es/): `mayor` rompe la costumbre o migra la base, `menor` agrega algo compatible, `parche` corrige.

## [Sin publicar]

Aquí se va acumulando lo que llevas hecho desde la última versión. Al publicar, esto se convierte en la sección de la versión nueva con su fecha, y este apartado vuelve a quedar vacío.

## [1.0.0] - 2026-08-30

Primera versión.

### El libro

- Movimientos con listas cerradas de cuentas y categorías: entradas, salidas, transferencias y saldos de apertura. Los importes viven como centavos enteros, así que un saldo en cero es exactamente cero.
- Transferencias en una sola captura, que escriben sus dos renglones ligados. Borrar uno borra el otro: nunca queda media transferencia.
- Patrimonio separado del consumo. Comprar un terreno o cripto es trasladar patrimonio, no gastarlo, y los tableros lo cuentan aparte.
- Ajustes de valor: capturas cuánto vale hoy el bien y se guarda la diferencia contra el saldo.
- Categorías con padre e hija, y descripción aparte de la categoría.

### Tableros y planeación

- Tablero con liquidez, deuda, patrimonio neto, meses de colchón y tasa de ahorro.
- Presupuesto por categoría y mes, con metas copiables al mes siguiente.
- Analítica de tendencia mensual.
- Compromisos con periodicidad de semanal a anual. El plan solo avanza cuando lo decides —cumplir o descartar— y las dos se deshacen devolviendo el plan justo a donde estaba, incluso uno que cae en día 31. Se resuelven igual desde el tablero, sin entrar a la lista.
- Aviso diario de compromisos por vencer, a la hora que elijas en Ajustes.

### Salud de los datos

- Nueve revisiones que corren en continuo: tipos que contradicen al signo del importe, transferencias sin pareja, saldos iniciales duplicados, movimientos sin categoría, medios incoherentes y descripciones casi idénticas, entre otras.
- Tres de ellas se reparan con un botón, siempre dentro de una sola transacción y sin tocar nunca el importe.

### Excel

- Exportación a `.xlsx` con fórmulas vivas —no tablas dinámicas— que se recalculan solas en Excel, WPS, LibreOffice y Sheets. Siete pestañas y dos esquemas de columna.
- Importación que reconoce encabezados sin acentos ni mayúsculas, corrige lo que puede mientras lee, empareja transferencias y crea las cuentas que falten. Es atómica: o entra todo o no entra nada.
- El paquete de Excel es propio, sin Apache POI.

### Seguridad y privacidad

- Base de datos cifrada con AES-256 (SQLCipher), con la llave envuelta en el Keystore del teléfono.
- Bloqueo opcional con la credencial del sistema o un PIN propio derivado con PBKDF2 y freno contra fuerza bruta.
- Nada de lo que capturas sale del teléfono. La única llamada a la red es preguntarle al sitio, una vez al día, si hay una versión más nueva; se apaga desde *Acerca de*.
- El respaldo automático de Android está desactivado para la base a propósito: tu respaldo es el `.xlsx` que tú exportas.

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-finanzas/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/carlosalbertoxw/ollin-finanzas/releases/tag/v1.0.0
