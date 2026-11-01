# Capturas de py.Studio

La galería conserva evidencia de la interfaz Android real. Las imágenes py.Studio siguientes se capturaron en la variante preview aislada en una Samsung Tab S8 (SM-X700, Android 16, modo DeX) y un Galaxy S25+ (SM-S936B, Android 16). Muestran el isotipo y la paleta actual en ambos temas. Se recortaron las barras del sistema y el dock DeX, sin alterar contenido de la app. El proyecto y los datos son de demostración. Las exploraciones conceptuales están en [propuestas de diseño](../design-proposals/README.md).

## py.Studio · Samsung Tab S8

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos | [PNG](py-studio-tab-s8/projects-light.png) | [PNG](py-studio-tab-s8/projects-dark.png) |
| Editor | [PNG](py-studio-tab-s8/editor-light.png) | [PNG](py-studio-tab-s8/editor-dark.png) |
| Notebook / Data Lab | [PNG](py-studio-tab-s8/notebook-light.png) | [PNG](py-studio-tab-s8/notebook-dark.png) |

La marca visible en el encabezado y el isotipo se verificaron en las seis capturas. La pantalla Editor mantiene el panel de código oscuro en ambos temas intencionalmente. La revisión del S25+ se documenta a continuación; ninguna de estas capturas representa una release publicada.

## py.Studio · Samsung Galaxy S25+

Capturas verticales a 1080 × 2340. La preview `com.pixelpy.editor.preview` se actualizó sobre sí misma; la app estable no se reemplazó. Las áreas de estado/notificaciones y navegación del sistema se excluyen de las imágenes.

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos | [PNG](py-studio-s25-plus/projects-light.png) | [PNG](py-studio-s25-plus/projects-dark.png) |
| Editor | [PNG](py-studio-s25-plus/editor-light.png) | [PNG](py-studio-s25-plus/editor-dark.png) |
| Notebook / Data Lab | [PNG](py-studio-s25-plus/notebook-light.png) | [PNG](py-studio-s25-plus/notebook-dark.png) |

Se comprobó en Editor que la fila de archivos y la de teclas auxiliares se desplazan horizontalmente; algunos elementos quedan parcialmente visibles hasta que el usuario descubre el gesto. Notebook agrupa sus acciones en **ACCIONES ▾** en este ancho. El diálogo de apariencia cabe en pantalla y cambiar el tema con las densidades expandidas no lo cierra. Esta revisión visual no equivale a certificar todos los tamaños, escalas del sistema o estados del teclado.

## Archivo histórico · identidad anterior

## Build anterior · Samsung Tab S8

Las capturas `orbit-code-tab-s8/` corresponden a la build preview aislada en una SM-X700 con Android 16 y a la identidad anterior. Las vistas de Editor, Notebook e importación CSV se recortaron para retirar la barra de estado y la barra de tareas de Samsung DeX; su contenido no se alteró.

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos y fondo Orbit | [PNG](orbit-code-tab-s8/projects-orbit-light.png) | [PNG](orbit-code-tab-s8/projects-orbit-dark.png) |
| Editor | [PNG](orbit-code-tab-s8/editor-light.png) | [PNG](orbit-code-tab-s8/editor-dark.png) |
| Data Lab / Notebook | [PNG](orbit-code-tab-s8/notebook-light.png) | [PNG](orbit-code-tab-s8/notebook-dark.png) |
| Importación CSV en constructor de gráfico | [PNG](orbit-code-tab-s8/chart-import-flow-light.png) | [PNG](orbit-code-tab-s8/chart-import-flow-dark.png) |

## Build anterior · Samsung Galaxy S25+

Las capturas `orbit-code-s25-plus/` se tomaron en el teléfono SM-S936B con Android 16 y la build preview aislada `com.pixelpy.editor.preview`; no reemplazó la instalación estable. Muestran la interfaz real en resolución 1080 × 2340 con la identidad anterior. Se recortaron las barras del sistema para excluir notificaciones y dejar solo la app. El proyecto y los datos visibles son de demostración.

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos y fondo Orbit | [PNG](orbit-code-s25-plus/projects-light.png) | [PNG](orbit-code-s25-plus/projects-dark.png) |
| Editor | [PNG](orbit-code-s25-plus/editor-light.png) | [PNG](orbit-code-s25-plus/editor-dark.png) |
| Data Lab / Notebook | [PNG](orbit-code-s25-plus/notebook-light.png) | [PNG](orbit-code-s25-plus/notebook-dark.png) |

| Ajustes de apariencia | [Diálogo y densidades](orbit-code-s25-plus/appearance-light.png) | — |

La captura adicional `appearance-density.png` muestra la lista expandible Compacta / Normal / Amplia en el propio diálogo. En el S25+ se comprobó el cambio del interruptor de tema con esa lista abierta. Esta galería se conserva únicamente como referencia histórica y no debe usarse para representar el logo actual.
