# Grupo IDEA - Relevamientos — V0.8 Referencias de obra

Aplicación Android nativa, offline-first, para relevamientos diarios de obra orientados a carpintería metálica.

## Flujo de campo

1. Abrir obra.
2. Entrar al sector.
3. Crear o duplicar un vano.
4. Cargar medidas y controles técnicos.
5. Tomar fotografías.
6. Clasificar la evidencia: Inicial / Incidencia / Corrección / Final.
7. Girar la foto si hace falta.
8. Agregar cotas, flechas, rectángulos, círculos o texto directamente sobre la imagen.
9. Registrar una incidencia desde el vano cuando sea necesario.
10. Compartir una foto técnica individual o generar el PDF completo de obra.

## Marca de empresa

Desde `Marca` en la pantalla principal se puede:

- editar el nombre de empresa o dejarlo vacío para usar solo el logo / datos técnicos;
- importar un logo PNG/JPG desde el teléfono;
- activar/desactivar el sello;
- decidir si el sello muestra fecha/hora, obra, sector y código de vano.

El archivo fotográfico original se conserva sin modificar. La marca y las anotaciones se renderizan en la visualización, al compartir la foto y dentro del PDF.

El nombre de empresa es opcional. Si se deja vacío, no se fuerza ningún texto corporativo en las fotografías ni en el encabezado del PDF.

## Herramientas incluidas

- Dashboard de obras con búsqueda y filtros.
- Obra → espacio/sector → vano → evidencias.
- Código de vano sugerido automáticamente.
- Duplicado rápido de vanos repetitivos.
- Estados Pendiente / Verificar / Relevado / Aprobado.
- Ancho, alto, antepecho, diagonales, profundidad, espesor y holguras.
- Checklist de plomo, nivel, escuadra, piso, revoque y premarco.
- Interferencias, sentido de apertura y observaciones.
- Cámara CameraX con cuadrícula y flash.
- Detector offline de candidatos de vano.
- Editor de evidencias con giro no destructivo.
- Cotas manuales con valor real.
- Flechas, rectángulos, círculos y notas de texto sobre fotografía.
- Foto principal por vano.
- Clasificación cronológica de fotos por etapa.
- Incidencias rápidas vinculadas al vano y a la bitácora.
- Bitácora automática y manual.
- Compartir JPG técnico con marca y anotaciones.
- Fotos generales del edificio obligatorias para generar el PDF; se muestran en la portada.
- PDF profesional con todas las evidencias, fichas y bitácora.
- Funcionamiento offline para relevamiento, fotos, base local y PDF.

## Medición fotográfica

La detección automática ubica candidatos visuales de vano, pero no convierte por sí sola píxeles en milímetros confiables. Las cotas ingresadas por el operario son la referencia documental. Una etapa posterior puede sumar calibración por referencia conocida y ARCore/Depth en dispositivos compatibles.

## Build

- Kotlin 2.0.21
- Jetpack Compose / Material 3
- Room 2.6.1
- CameraX 1.4.1
- compileSdk / targetSdk 35
- Java 17
- versionCode 5
- versionName 0.8.0
