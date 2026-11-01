# Diseño y recorridos de PixelPy

PixelPy sigue la referencia visual RCK1996 con retícula de dibujo, órbitas y filetes discretos. Orbit Code sustituye a Pixel Spark en cabecera e iconos adaptativos. El modo oscuro usa navy, marfil y azul eléctrico; el claro, papel cálido y terracota. Ambos comparten jerarquía, fondo técnico y componentes. Los tokens y usos están en la [guía de identidad visual](identidad-visual.md). Cada pantalla debe comunicar dónde estás, qué puedes hacer y qué ocurrió después de una acción.

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
- El proyecto activo se identifica por su ruta canónica y se resalta con el acento del tema.
- La retícula y el diagrama orbital se perciben desde Proyectos y continúan bajo las barras superior e inferior; las tarjetas conservan contraste y lectura.
- Proyectos ofrece accesos de tarea a Data Lab y Automatizaciones, con descripciones que explican qué pasa al abrirlos; se apilan en teléfonos estrechos.
- La navegación inferior conserva contraste en ambos modos; los botones principales usan el acento y los contornos se reservan para separación y foco.

- Atrás cierra Notebook o Automatizaciones, vuelve de Consola a Editor y desde Editor o REPL vuelve a Proyectos.
- La barra inferior identifica Proyectos cuando se abre una herramienta del proyecto.
- Una solicitud de entrada del script permite enviar la respuesta o detener la ejecución.
- El historial de deshacer se reinicia al cambiar de archivo para evitar aplicar contenido de otro script.
- Cada carpeta de recursos vacía muestra una explicación y acceso directo a importar.
- La selección de recursos se reinicia al cambiar de proyecto.
- La consola distingue espera inicial, ejecución y finalización sin salida.
- Desde un error de ejecución, el salto a línea conserva el archivo ejecutado y desplaza el editor para mostrar el cursor, incluso si la línea queda fuera de la zona visible.
- Las automatizaciones explican cuándo están pausadas o aún no tienen resultado.
- El notebook presenta progreso y una acción principal de ejecución; el constructor de gráficos puede desplazarse verticalmente.
- Si falta el CSV para un gráfico, se puede importarlo desde el constructor sin abandonar el flujo; la importación valida extensión, tamaño y colisiones de nombre.

## Validación histórica de 1.8.0

Compilación de producción firmada, pruebas unitarias y lint completados. Instalación como actualización en Samsung S25+ con Android 16, conservando proyectos. Comprobados Proyectos, Editor, entrada de texto del script, salida correcta en Consola, navegación Atrás, acceso al notebook y pantalla vacía de Automatizaciones. Las capturas `studio-*.png` son evidencia histórica de Pixel Studio 1.8.0; la galería vigente Orbit Code está en el README.

## Validación visual Orbit Code

APK de desarrollo aislado instalado en Samsung Tab S8 y Samsung Galaxy S25+ (SM-S936B, Android 16), sin reemplazar la app estable. En ambos dispositivos se revisaron Proyectos, Editor y Data Lab en los dos temas. En el S25+ la órbita se aparta del contenido principal para adaptarse al ancho, y las tres densidades se eligen desde una lista expandible dentro del diálogo. Se verificó en el teléfono que cambiar el interruptor de tema mientras la lista está abierta no oculta ni deja sin repintar el diálogo; también se comprobó la selección de Compacta. La compilación, lint y 76 pruebas unitarias pasaron. Las galerías incluyen capturas reales de cada equipo y del diálogo de apariencia. El detalle y los límites están en el [informe de QA](QA_REPORT.md).

## Cobertura adicional

Los cambios de interfaz requieren comprobarse en Android con teclado abierto, nombres largos, fuente ampliada y pantallas pequeñas. La compilación por sí sola no verifica los gestos, el contraste ni los recorridos completos.
