# Contribuir

Ollin Finanzas es un proyecto personal, pero el código es abierto ([MIT](LICENSE)) y los reportes y propuestas se leen.

- **Un fallo o una idea:** abre un *issue*. Si la app se cerró sola, *Acerca de* tiene el informe del último fallo; copiarlo en el issue ahorra la mitad de las preguntas.
- **Un problema de seguridad:** no en un issue público. Ver [SECURITY.md](SECURITY.md).
- **Un cambio de código:** mejor abrir antes un issue para hablarlo. La app tiene decisiones que no son las de por defecto —XLSX escrito a mano, sin Hilt, sin `fallbackToDestructiveMigration`, montos en centavos— y están explicadas en el [README](README.md#arquitectura) y en [docs/](docs/); un PR que las deshace sin hablarlo probablemente no entre.

## Cómo se trabaja

- **Ramas cortas y pull request a `main`.** El flujo [`pruebas.yml`](.github/workflows/pruebas.yml) tiene que quedar en verde: licencias, pruebas unitarias, Lint, ktlint, la suite instrumentada compilada, `assembleRelease` y el sitio. `./gradlew ktlintFormat` arregla lo que ktlint sabe arreglar solo.
- **Commits que dicen qué cambia para quien usa la app**, en español y en presente: *«El candado se cierra al salir»*, no *«fix»*. El cuerpo explica el porqué.
- **El CHANGELOG en el mismo cambio.** Lo que se nota en la app va bajo `## [Sin publicar]`, con el formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). De ahí salen la versión del APK y las notas de la release, así que lo que no está ahí no se publica. Ver [publicación](docs/publicacion.md).
- **La documentación en el mismo cambio que la vuelve obsoleta.** Si un comando, un archivo o una regla cambia, cambia también donde se explica.
- **Una prueba con cada corrección.** La que habría fallado antes del arreglo.
- **Una dependencia nueva, con su huella.** El build verifica cada artefacto contra [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml), y uno que no esté ahí lo rompe. Cómo regenerarlo, en [desarrollo](docs/desarrollo.md#dependencias-verificadas).
- **Quien firma el commit responde por él, lo haya escrito como lo haya escrito.** Parte de este código se escribe con ayuda de asistentes de IA, y esos commits lo declaran con `Co-Authored-By`. El asistente no cambia nada de lo anterior: el cambio se lee entero antes de entrar, pasa las mismas pruebas y su autor humano tiene que poder explicar cada línea. En un PR externo vale lo mismo; si usaste una de estas herramientas, dilo en la descripción.
- **Una versión nueva de la base, con su migración y su prueba.** Nunca un cambio de esquema sin `Migration`: la base va cifrada con una llave que no se respalda, y perderla es perder el libro. Ver [modelo de datos](docs/modelo-de-datos.md).

El entorno, los comandos y las convenciones del código están en [docs/desarrollo.md](docs/desarrollo.md).
