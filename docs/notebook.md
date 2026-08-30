# Notebook local de PixelPy

Notebook permite combinar explicación y análisis Python dentro de un proyecto, sin servidores ni conexión obligatoria. Cada proyecto mantiene sus propios notebooks y PixelPy guarda los cambios automáticamente en su almacenamiento privado.

## Flujo básico

1. Abre **Proyectos** y pulsa **NOTEBOOK**.
2. Elige un notebook existente o crea uno desde el selector superior.
3. Añade una celda de **CÓDIGO** o **MARKDOWN**.
4. Usa **CELDA** para ejecutar una celda o **EJECUTAR TODO** para reconstruir el análisis completo.
5. Consulta debajo de cada celda Python su salida, resultados enriquecidos, estado y duración.

Las celdas pueden subirse, bajarse, eliminarse o cambiarse entre Python y Markdown desde su menú `⋮`. La salida puede plegarse para mantener visible el código importante.

El selector permite buscar por nombre cuando el proyecto contiene muchos notebooks. El documento en uso aparece resaltado con **ACTIVO** y no puede volver a abrirse por accidente. Las acciones de renombrar, eliminar y abrir se distribuyen en dos filas para seguir siendo utilizables en pantallas angostas.

## Importar, exportar y duplicar

La barra de herramientas permite:

- importar archivos Jupyter `.ipynb` desde el almacenamiento del teléfono;
- exportar el notebook actual a `.ipynb` compatible con Jupyter;
- duplicar el notebook actual para crear una variante independiente.
- renombrar o eliminar notebooks desde el selector, conservando siempre al menos uno;
- buscar rápidamente un notebook por su nombre.

La conversión conserva las celdas Python y Markdown. Las salidas, gráficos incrustados y metadatos específicos del kernel no se exportan; las celdas se vuelven a ejecutar localmente en PixelPy.

## Estado compartido y ejecución reproducible

Las celdas Python se evalúan desde arriba hasta la celda elegida dentro de un espacio de variables nuevo. Una variable creada en una celda anterior puede utilizarse después:

```python
# Celda 1
ventas = [12, 18, 25, 31]
```

```python
# Celda 2
print("Promedio:", sum(ventas) / len(ventas))
```

Al volver a ejecutar una celda, PixelPy reconstruye primero las celdas Python anteriores. Esto evita depender de un estado oculto distinto del código visible. **EJECUTAR TODO** conserva una salida independiente para cada celda y se detiene ante el primer error.

Cuando se modifica una celda anterior, las salidas posteriores se conservan como referencia pero aparecen marcadas como **desactualizadas** hasta volver a ejecutarlas. Así una salida histórica no se confunde con el código actual.

Notebook utiliza el mismo coordinador de Python que Editor, DEBUG, REPL y Automatizaciones. Nunca se ejecutan dos scripts Python simultáneamente.

## Markdown

Las celdas Markdown tienen dos modos: **VISTA**, que muestra el documento renderizado, y **EDITAR**, que permite modificar el texto original.

La vista admite títulos, párrafos, listas con viñetas y numeradas, citas, bloques de código y formato básico.

## Archivos y resultados

El código se ejecuta en la carpeta del proyecto activo. Los archivos creados o modificados aparecen después en **Recursos**, desde donde pueden previsualizarse, organizarse o compartirse.

El visor CSV muestra una tabla desplazable con búsqueda, filas y columnas. Los archivos grandes siguen limitados para evitar bloqueos; el archivo completo puede abrirse externamente.

`display(valor)` permite presentar datos sin convertirlos manualmente a texto. PixelPy reconoce tablas, JSON y valores simples. Los resultados se guardan de forma estructurada y permanecen asociados a la celda que los produjo. Los archivos nuevos también aparecen en esa celda con su vista previa integrada.

El inspector **VARIABLES** resume los valores compartidos sin intentar mostrar objetos completos o estructuras enormes. Limita profundidad, cantidad y longitud para mantener fluida la interfaz.

## Gráficos desde CSV

En **ACCIONES ▾ → CREAR GRÁFICO** puedes elegir un CSV del proyecto, las columnas X/Y y el tipo línea, barras o puntos. PixelPy añade una celda Python editable y reproducible que genera un SVG local mediante `pixelpy_chart`; no requiere Internet ni Matplotlib. El SVG aparece como resultado de la misma celda y también en Recursos.

En pantallas amplias la acción se muestra directamente; en teléfonos queda dentro del menú desplegable para evitar botones estrechos.

## Respaldos

Desde las acciones del proyecto se puede crear o restaurar un respaldo. Incluye scripts, recursos, notebooks y datos ocultos del proyecto. PixelPy conserva los cinco más recientes, valida las rutas del ZIP y crea un respaldo del estado actual antes de restaurar otro.

## Límites actuales

- `input()` no abre diálogos dentro de Notebook y produce un error claro en la celda que lo solicita.
- Una ejecución completa tiene un límite máximo de 120 segundos.
- La importación y exportación `.ipynb` no conserva salidas ni gráficos incrustados.
- Notebook es local: no incluye colaboración, sincronización ni ejecución en servidores.

## Persistencia y privacidad

PixelPy guarda el documento de forma atómica dentro del proyecto privado. El notebook incluye sus celdas, Markdown, salida, resultados estructurados, estado y duración. Los documentos anteriores se migran automáticamente al abrirlos. No se envía contenido a Internet salvo que el propio código Python realice una solicitud de red.
