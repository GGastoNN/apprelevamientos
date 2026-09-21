# Grupo IDEA - Relevamientos — V0.6 Professional Daily

Aplicación Android nativa, offline-first, para relevamientos de obra orientados a carpintería metálica.

## Objetivo de esta versión

Reducir al mínimo los pasos durante el recorrido diario de obra y dejar trazabilidad suficiente para reconstruir qué se midió, fotografió, observó o decidió.

## Flujo rápido

1. Abrir obra.
2. Entrar al espacio/sector.
3. Crear vano con código sugerido automáticamente (`V01`, `V02`, ...).
4. Cargar medidas y controles técnicos.
5. Tomar foto.
6. La app ejecuta detección offline de candidatos de vano.
7. Dibujar cotas sobre la fotografía con dos toques y escribir el valor real.
8. Agregar comentario de foto y marcar una imagen principal.
9. Registrar incidencias/decisiones en Bitácora.
10. Generar y compartir un PDF técnico completo.

## Incluido

- Dashboard de obras con búsqueda y filtros por estado.
- Obras con cliente, dirección, responsable, observaciones y estado.
- Espacios/sectores con planta, sector y notas.
- Alta rápida de vanos con numeración sugerida.
- Estados de vano: Pendiente, Verificar, Relevado y Aprobado.
- Medidas principales y diagonales en milímetros.
- Espesor de muro, profundidad y holguras laterales/superior/inferior.
- Checklist rápido: plomo, nivel, escuadra, piso, revoque y premarco.
- Sentido/condición de apertura, interferencias y observaciones.
- Cámara CameraX con cuadrícula y flash Auto/On/Off.
- Detector offline experimental de vanos rectangulares.
- Editor fotográfico con candidatos detectados y cotas manuales sobre la imagen.
- Comentarios por fotografía, imagen principal, giro de 90° y deshacer última cota.
- Bitácora cronológica por obra con notas, alertas y decisiones.
- Registro automático de altas, fotos, mediciones y cambios de estado.
- Informe PDF por obra con portada, resumen, espacios, fichas técnicas, todas las fotos con cotas y bitácora.
- Base Room local y migraciones V0.1 → V0.5 → V0.6 preservando los datos existentes.
- Funcionamiento sin Internet para relevamiento, cámara, base local y PDF.
- GitHub Actions actualizado para generar APK debug.

## Importante sobre las mediciones

La detección del vano en una foto es una ayuda visual. Una fotografía 2D no permite deducir milímetros absolutos con precisión sin referencia de escala o profundidad. Las cotas ingresadas por el usuario son la fuente documental. Una siguiente etapa puede sumar calibración por referencia conocida y ARCore/Depth cuando el dispositivo sea compatible.

## Build

- Kotlin 2.0.21
- Jetpack Compose / Material 3
- Room 2.6.1
- CameraX 1.4.1
- compileSdk / targetSdk 35
- Java 17
- versionCode 3
- versionName 0.6.0
