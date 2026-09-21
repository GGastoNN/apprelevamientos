# Grupo IDEA - Relevamientos V0.6

- Agrega controles **Girar ↺ / Girar ↻** en el editor de evidencias, en pasos de 90°.
- Giro no destructivo de fotografías: el archivo JPG original no se recomprime.
- Persistencia de orientación por foto (`0/90/180/270°`).
- Miniaturas y editor respetan la orientación elegida.
- Las cotas existentes se muestran correctamente después del giro.
- Las cotas nuevas se almacenan en el sistema de coordenadas original para conservar compatibilidad.
- Los candidatos de vano detectados también rotan con la imagen.
- El PDF exporta cada fotografía con la orientación corregida y las cotas en su posición correspondiente.
- Room sube a versión 3 con migración 2 → 3 sin borrar datos.
- App `versionName 0.6.0`, `versionCode 3`.
