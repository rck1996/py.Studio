# Informe de QA de py.Studio

Este informe separa compilación automatizada, pruebas unitarias y revisión visual en dispositivo. Una build de vista previa no equivale a una versión publicada.

## Build Orbit Code · 2026-10-31

| Comprobación | Resultado | Evidencia / alcance |
| :--- | :--- | :--- |
| Pruebas unitarias | **76 pasaron** | `:pyeditor:testDevelopmentDebugUnitTest`; cero fallos y omisiones |
| Android lint | **Correcto** | `:pyeditor:lintDevelopmentDebug`; se mantienen avisos deprecados existentes |
| APK de desarrollo | **Compilado** | `:pyeditor:assembleDevelopmentDebug`; variante aislada `com.pixelpy.editor.preview` |
| Samsung Tab S8 · SM-X700 · Android 16 | **Revisión visual realizada** | Proyectos, Editor y Data Lab en claro y oscuro; capturas en la galería Orbit Code |
| Samsung Galaxy S25+ · SM-S936B · Android 16 | **Revisión visual realizada** | Proyectos, Editor y Data Lab en claro y oscuro; APK preview aislada, capturas reales en `orbit-code-s25-plus/` |

La app estable `com.pixelpy.editor` no se reemplazó durante esta verificación. El paquete de vista previa conserva almacenamiento separado para proteger los datos de producción.

## Cambio de identidad py.Studio

El repositorio y los recursos de marca ya usan py.Studio, con símbolo de capas y paleta naranja/crema/negro/neutro. La preview se compiló, instaló y revisó en Tab S8 y Galaxy S25+; las capturas de Proyectos, Editor y Notebook en ambos temas están en [la galería py.Studio](screenshots/README.md). Se verificaron nombre e isotipo de cabecera; la app estable no se reemplazó. Esta verificación visual no es una release.

En el Galaxy S25+ (SM-S936B, Android 16, 1080 × 2340) el notebook cambia a menú desplegable de acciones, el diálogo de apariencia cabe y el tema se puede cambiar con el selector de densidad expandido. En Editor, tanto la tira de archivos como la fila de teclas auxiliares admiten desplazamiento horizontal para alcanzar sus elementos, aunque el contenido parcial en el borde no hace obvio ese gesto. Se registra como oportunidad de mejora de descubrimiento, no como bloqueo funcional. Evidencia visual: [capturas del S25+](screenshots/README.md).

## Qué cubre la revisión visual

En el S25+ (1080 × 2340, escala de fuente del sistema 0,9) la órbita se redujo y desplazó hacia el borde superior derecho para liberar el contenido en el ancho del teléfono. Se comprobó Proyectos, Editor y Data Lab en los dos temas. El selector de densidad muestra Compacta, Normal y Amplia sin partir las etiquetas; el diálogo de apariencia cabe en pantalla. La galería incluye capturas de la app real, recortadas para excluir iconos y barras del sistema, más una vista del selector expandido.

**Anomalía corregida y verificada en el S25+:** el menú emergente separado de densidad podía cerrarse mientras se tocaba el interruptor de tema y dejar el diálogo sin repintar hasta otro toque. Se sustituyó por una lista expandible integrada en el diálogo. Se repitió la secuencia con las opciones abiertas y se tocó el interruptor: el título, ambos controles, las tres densidades y los botones de acción permanecieron visibles; Compacta también se seleccionó y el valor se reflejó al cerrar la lista. Evidencia: [Apariencia](screenshots/orbit-code-s25-plus/appearance-light.png) y [densidades desplegadas](screenshots/orbit-code-s25-plus/appearance-density.png).

La variante `developmentDebug` usa ahora el identificador aislado `com.pixelpy.editor.preview`; el proveedor de archivos también deriva su autoridad del ID de paquete. La preview se instaló sobre la preview existente en el S25+, sin desinstalar ni reemplazar `com.pixelpy.editor`.

## Límites

- La prueba unitaria no sustituye los recorridos de UI en todos los tamaños, escalas de fuente y teclados.
- La revisión visual no certifica rendimiento prolongado, accesibilidad exhaustiva, rotación ni retrasos de tareas por políticas del fabricante.
- La release estable `1.8.0` conserva la identidad anterior; las capturas py.Studio son de una preview de desarrollo.

Consulta [Experiencia de usuario](experiencia-de-usuario.md) para recorridos y límites funcionales, y [Capturas](screenshots/README.md) para identificar origen y estado de cada imagen.
