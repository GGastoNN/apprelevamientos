# Grupo IDEA - Relevamientos — V0.1

Base Android nativa (Kotlin + Jetpack Compose) para relevamientos de obra orientados a carpintería metálica.

## Incluido en esta base
- Obras, espacios/sectores y vanos.
- Cotas básicas en milímetros.
- Comentarios e incidencias por vano.
- Captura de fotografías con CameraX en almacenamiento privado de la app.
- Base de datos local Room (offline-first).
- Registro de eventos por obra preparado para trazabilidad.
- Detector offline experimental de vanos rectangulares (`OpeningDetector`).
- Motor PDF sin librerías comerciales (`ReportPdf`) preparado para informe por obra.
- GitHub Actions para generar APK debug.

## Próxima etapa
1. Editor visual de fotografías: rectángulo/polígono del vano y cotas dibujadas.
2. Confirmación/ajuste manual de las detecciones automáticas.
3. Calibración por referencia conocida para convertir píxeles a mm.
4. Pantalla Timeline: fotos, notas, modificaciones, problemas y visitas por fecha/hora.
5. Exportación y compartir PDF desde la interfaz.
6. Firma/aceptación de responsable de obra y checklist de condiciones del vano.
7. Backup/sincronización opcional sin perder funcionamiento offline.

## Medición
La detección de un vano en una fotografía no equivale a medirlo físicamente. Una cámara 2D no permite inferir milímetros absolutos con precisión sin una referencia de escala, información de profundidad o una medición ingresada por el usuario. Por eso el diseño usa cotas manuales/calibradas como fuente de verdad.
