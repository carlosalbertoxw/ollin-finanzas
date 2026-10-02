# Seguridad

## Reportar una vulnerabilidad

Si encuentras un problema de seguridad en Ollin Finanzas —en la app, en el sitio de descarga o en el flujo que publica las versiones—, **no abras un issue público**. Repórtalo en privado desde la pestaña [Security](https://github.com/carlosalbertoxw/ollin-finanzas/security/advisories/new) del repositorio (*Report a vulnerability*).

Ayuda mucho incluir:

- la versión de la app (*Acerca de*) y de Android;
- qué se puede hacer y qué hace falta para hacerlo (el teléfono en la mano, un archivo `.xlsx` preparado, estar en la misma red…);
- los pasos para reproducirlo.

Es un proyecto personal: no hay recompensas ni plazos garantizados, pero cada reporte se lee, se contesta y, si se confirma, se corrige en una versión de parche con su entrada en el [CHANGELOG](CHANGELOG.md).

## Versiones que reciben correcciones

Solo la última publicada. Android no permite volver a una versión anterior, así que las correcciones siempre llegan como una versión nueva.

## Qué protege la app y qué no

Está descrito en [docs/seguridad.md](docs/seguridad.md): el cifrado de la base, el candado, los avisos, los respaldos y la única llamada a internet. Vale la pena leerlo antes de reportar: algunos límites están ahí a propósito, como que un teléfono con root puede usar la llave del Keystore aunque no pueda copiarla.

## Comprobar un APK

Todas las versiones van firmadas con la misma llave. La huella de su certificado está en el [README](README.md#comprobar-que-el-apk-es-el-bueno). Un APK con otra huella no salió de este repositorio, aunque diga llamarse Ollin Finanzas.
