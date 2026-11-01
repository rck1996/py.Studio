# Identidad visual de PixelPy

PixelPy adapta el lenguaje de la [guía visual RCK1996](https://www.rck1996.com/guide-style) al trabajo de un editor Android: composición editorial, retícula de dibujo tenue, órbitas y líneas de construcción, jerarquía tipográfica condensada para títulos, texto legible y monoespaciada para código, filetes finos y esquinas sobrias. Los dos temas comparten estructura y componentes; solo cambian sus roles de color.

## Paleta de referencia

| Rol | Oscuro | Claro | Uso |
| --- | --- | --- | --- |
| Fondo | `#020B1B` | `#E9E4DA` | Lienzo principal |
| Superficie | `#041126` | `#F5F1E7` | Tarjetas, filas y paneles |
| Tinta principal | `#F1EDDF` | `#171919` | Títulos y contenido |
| Texto secundario | `#BAC4D4` | `#55564F` | Metadatos y estados |
| Acción principal | `#5E9EFF` | `#E34B12` | Acción primaria, selección y foco |
| Tinta del acento | `#061124` | `#FFF8EB` | Contraste sobre acción primaria |
| Línea técnica | `#9EB2CE42` | `#282D3045` | Bordes y retícula de baja intensidad |
| Acento de título | `#F1EDDF` | `#D83F0D` | Encabezados de énfasis |

La retícula nativa se dibuja cada 48 dp; cada cuarta línea marca una subdivisión editorial mayor. Un diagrama orbital de trazo terracota/azul eléctrico, con anillos, elipse inclinada, nodos y marcas de registro, ocupa el espacio periférico. En teléfonos se reduce y desplaza hacia el borde superior para no atravesar títulos, controles ni pestañas; en tabletas dispone de más lienzo. La retícula cruza de forma continua las barras superior e inferior y el contenido; las superficies conservan opacidad y filetes finos para proteger legibilidad. El motivo es más visible en el espacio libre que detrás de controles.

## Imagen y marca

La guía combina ilustraciones editoriales grabadas, diagramas orbitales SVG, textura de papel/grano y capturas de sus productos. PixelPy traduce esa mezcla a su contexto: papel cálido en claro y navy en oscuro, retícula de dibujo y órbitas como textura ambiental, marca Orbit Code propia y capturas reales de la app. No reutiliza los grabados ajenos al producto como wallpaper; reserva las imágenes de la app para comunicar funciones y mantiene despejado el lienzo donde se trabaja.

El símbolo une delimitadores de código, nodos de datos y una órbita. Se distribuye como vector Android, icono adaptativo, capa monocroma para Android 13+ y SVG documental.

![Marca PixelPy Orbit Code](assets/pixelpy-orbit-code.svg)

## Composición y controles

- Títulos condensados y breves; cuerpo de lectura despejado; rutas, datos y código en monoespaciada.
- Fondo técnico compartido por proyectos, editor, notebooks, consola, automatizaciones y resultados; barras transparentes continúan el lienzo, y tarjetas opacas mantienen la legibilidad.
- El lienzo de edición conserva su superficie oscura para separar el código y sostener el contraste de sintaxis en ambos temas; el marco, la navegación y las acciones sí siguen el tema elegido.
- Acento eléctrico en oscuro y terracota en claro, con el mismo rol, peso visual y jerarquía.
- Acciones y pestañas seleccionadas usan el acento; paneles y diálogos conservan superficies neutras.
- En pantallas estrechas, las acciones secundarias se mantienen desplazables o dentro de listas/menús adaptables.

## Temas y validación

La opción **Tema oscuro** en Apariencia cambia la app, las barras del sistema, el selector del widget y el visor de resultados. **Contraste alto** y escala de interfaz se conservan como ajustes independientes. El widget anclado usa una variante nocturna estable para respetar el fondo que Android le asigna.

Las capturas por dispositivo documentan la retícula y el motivo orbital en Tab S8 y Galaxy S25+; son pantallas de la app, sin marcos ni mockups. En ambos temas se deben comprobar contraste de texto, selección activa, estados de ejecución, campos, menús, tablas CSV, celdas del notebook y diálogos. Consulta la [galería](screenshots/README.md) y el [informe de QA](QA_REPORT.md) para distinguir lo revisado de las anomalías pendientes.
