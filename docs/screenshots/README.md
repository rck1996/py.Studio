# Capturas de PixelPy

La galería conserva evidencia de la interfaz Android real. Cada imagen vigente identifica dispositivo, pantalla y tema; las exploraciones conceptuales permanecen en [propuestas de diseño](../design-proposals/README.md) y no se presentan como capturas del producto.

## Orbit Code · Samsung Tab S8

Las capturas `orbit-code-tab-s8/` corresponden a la build preview aislada en una SM-X700 con Android 16. `projects-orbit-light.png` y `projects-orbit-dark.png` muestran la retícula editorial y el diagrama orbital actuales. Las vistas de Editor, Notebook e importación CSV se recortaron para retirar la barra de estado y la barra de tareas de Samsung DeX; no se alteró el contenido de PixelPy.

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos y fondo Orbit | [PNG](orbit-code-tab-s8/projects-orbit-light.png) | [PNG](orbit-code-tab-s8/projects-orbit-dark.png) |
| Editor | [PNG](orbit-code-tab-s8/editor-light.png) | [PNG](orbit-code-tab-s8/editor-dark.png) |
| Data Lab / Notebook | [PNG](orbit-code-tab-s8/notebook-light.png) | [PNG](orbit-code-tab-s8/notebook-dark.png) |
| Importación CSV en constructor de gráfico | [PNG](orbit-code-tab-s8/chart-import-flow-light.png) | [PNG](orbit-code-tab-s8/chart-import-flow-dark.png) |

## Orbit Code · Samsung Galaxy S25+

Las capturas `orbit-code-s25-plus/` se tomaron en el teléfono SM-S936B con Android 16 y la build preview aislada `com.pixelpy.editor.preview`; no reemplazó la instalación estable. Muestran la interfaz real en resolución 1080 × 2340. Se recortaron las barras del sistema para excluir notificaciones y dejar solo la app. El proyecto y los datos visibles son de demostración.

| Vista | Claro | Oscuro |
| :--- | :--- | :--- |
| Proyectos y fondo Orbit | [PNG](orbit-code-s25-plus/projects-light.png) | [PNG](orbit-code-s25-plus/projects-dark.png) |
| Editor | [PNG](orbit-code-s25-plus/editor-light.png) | [PNG](orbit-code-s25-plus/editor-dark.png) |
| Data Lab / Notebook | [PNG](orbit-code-s25-plus/notebook-light.png) | [PNG](orbit-code-s25-plus/notebook-dark.png) |

| Ajustes de apariencia | [Diálogo y densidades](orbit-code-s25-plus/appearance-light.png) | — |

La captura adicional `appearance-density.png` muestra la lista expandible Compacta / Normal / Amplia en el propio diálogo. En el S25+ se comprobó el cambio del interruptor de tema con esa lista abierta: los controles y las etiquetas se mantienen visibles. La corrección y su alcance están descritos en el [informe de QA](../QA_REPORT.md). El símbolo Orbit Code que aparece en la cabecera de las capturas coincide con el SVG y los recursos adaptativos documentados en [Identidad visual](../identidad-visual.md).
