# Editor de código

Abre un script tocando su fila en Proyectos. El editor conserva resaltado Python, números de línea, autoguardado, búsqueda y versiones.

Si una ejecución falla y la consola indica una línea, **IR A LÍNEA** abre el archivo realmente ejecutado (con sus cambios guardados) y desplaza el editor hasta dejar el cursor visible. Así puedes corregir y volver a ejecutar sin buscar manualmente el punto del error.

## Escritura móvil

- **Deshacer / Rehacer:** disponibles en la barra superior incluso con el teclado abierto. El historial se separa al cambiar de archivo.
- **⇥:** añade cuatro espacios a la línea actual o a las líneas seleccionadas.
- **⇤:** retira hasta cuatro espacios o una tabulación de las líneas seleccionadas.
- **# /:** comenta o descomenta las líneas seleccionadas conservando su sangría.
- **TAB y símbolos:** inserta espacios, pares de paréntesis, corchetes y comillas. Un par envuelve el texto seleccionado.
- **Ln / Col:** indica la posición del inicio de selección; las columnas cuentan caracteres, no el ancho visual de tabulaciones.

El campo solicita teclado ASCII sin autocorrección ni mayúsculas automáticas. El comportamiento final depende del teclado instalado.

Las sugerencias incluyen nombres del código y scripts del proyecto. No ejecutan Python ni realizan inferencia de tipos; los archivos se obtienen de la lista del proyecto sin recorrer carpetas al escribir.

## Herramientas

La lupa abre búsqueda y reemplazo. En **⋮** puedes depurar, consultar funciones y clases, recuperar versiones y ajustar el tamaño del texto.

El panel del código conserva fondo oscuro para leer la sintaxis en los dos temas. El resto del editor sigue los colores de superficie del tema activo y evita la antigua sombra desplazada. Consulta [Identidad visual](identidad-visual.md) para la paleta y contraste claro/oscuro.
