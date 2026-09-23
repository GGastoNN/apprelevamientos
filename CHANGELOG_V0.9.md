# V0.9 PDF compacto y selección de fotografías

## Exportación PDF

- El informe deja de crear una página de ficha más una página por cada evidencia.
- Cada vano se presenta en una única hoja compacta con medidas, controles, observaciones y la fotografía elegida.
- Se eliminan las páginas intermedias de resumen por espacio para reducir la cantidad total de hojas.
- Las fotografías de vano usadas en el PDF se limitan a 1200 px en su lado mayor para reducir el peso del archivo sin perder legibilidad en la hoja A4.
- Las imágenes de portada se cargan a una resolución menor, adecuada al tamaño con el que se imprimen.

## Selección previa de fotografías

- Al tocar `PDF` se abre primero una pantalla de preparación del informe.
- Para cada vano se selecciona automáticamente la última fotografía tomada.
- El usuario puede revisar la miniatura, cambiar por otra evidencia del mismo vano o elegir `No incluir foto`.
- La selección afecta únicamente a ese PDF; no modifica ni elimina las fotos del relevamiento.

## Versión Android

- versionCode 6
- versionName 0.9.0
- Sin cambios de esquema Room respecto de V0.8.
