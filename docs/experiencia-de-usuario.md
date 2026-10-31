# Diseño y recorridos de PixelPy

PixelPy adopta Pixel Studio: superficies crema, acentos menta, amarillo y periwinkle, esquinas de 12 dp y bordes definidos. El logo Pixel Spark se utiliza en cabecera, icono adaptativo y versión monocroma. Cada pantalla debe comunicar dónde estás, qué puedes hacer y qué ocurrió después de una acción.

## Recorridos principales

| Recorrido | Entrada | Acción principal | Continuación |
| --- | --- | --- | --- |
| Programar | Proyectos → script | Abrir y editar | Ejecutar → Consola → Editar |
| Explorar datos | Proyectos → Recursos | Importar en la carpeta seleccionada | Vista previa o notebook |
| Analizar por celdas | Proyectos → Notebook | Seleccionar notebook y ejecutar | Consultar salida y archivos de cada celda |
| Programar tareas | Proyectos → Automatizaciones | Crear y activar | Consultar detalles, historial y resultado |
| Experimentar | REPL | Ejecutar un comando | Continuar en la misma sesión del proyecto |

## Comportamientos implementados

- En Proyectos los scripts se abren tocando su fila; las operaciones secundarias siguen en el menú de cada archivo.
- Scripts y Recursos usan chips compactos; el botón de nuevo script se sitúa junto a ellos.
- El proyecto activo se identifica por su ruta canónica y se resalta en menta.
- La navegación inferior usa fondo claro y los botones principales evitan contornos negros repetidos.

- Atrás cierra Notebook o Automatizaciones, vuelve de Consola a Editor y desde Editor o REPL vuelve a Proyectos.
- La barra inferior identifica Proyectos cuando se abre una herramienta del proyecto.
- Una solicitud de entrada del script permite enviar la respuesta o detener la ejecución.
- El historial de deshacer se reinicia al cambiar de archivo para evitar aplicar contenido de otro script.
- Cada carpeta de recursos vacía muestra una explicación y acceso directo a importar.
- La selección de recursos se reinicia al cambiar de proyecto.
- La consola distingue espera inicial, ejecución y finalización sin salida.
- Las automatizaciones explican cuándo están pausadas o aún no tienen resultado.
- El notebook presenta progreso y una acción principal de ejecución; el constructor de gráficos puede desplazarse verticalmente.

## Validación de 1.8.0

Compilación de producción firmada, pruebas unitarias y lint completados. Instalación como actualización en Samsung S25+ con Android 16, conservando proyectos. Comprobados Proyectos, Editor, entrada de texto del script, salida correcta en Consola, navegación Atrás, acceso al notebook y pantalla vacía de Automatizaciones. Capturas reales disponibles en `docs/screenshots/studio-*.png`.

## Cobertura adicional

Los cambios de interfaz requieren comprobarse en Android con teclado abierto, nombres largos, fuente ampliada y pantallas pequeñas. La compilación por sí sola no verifica los gestos, el contraste ni los recorridos completos.
