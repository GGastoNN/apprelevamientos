# V0.8 Referencias de obra

## Identidad de la aplicación

- Se incorpora el nuevo logo provisto como ícono de la aplicación.
- El mismo logo se muestra en la cabecera de la pantalla principal.

## Marca configurable

- El nombre de empresa pasa a ser opcional.
- Se puede guardar el campo vacío sin que la app lo reemplace por `Grupo IDEA`.
- Fotografías, miniaturas, exportaciones y encabezados PDF respetan el nombre vacío.
- Si hay logo pero no nombre, se muestra únicamente el logo junto con los datos técnicos habilitados.

## Referencias del edificio para el PDF

- Nueva entidad Room `project_reference_photos` asociada a cada obra.
- Desde la pestaña `Obra` se pueden seleccionar varias imágenes generales del edificio.
- El PDF exige al menos una imagen válida de referencia antes de generarse.
- La portada muestra hasta tres imágenes del edificio y contabiliza referencias adicionales.
- Migración de base de datos 4→5 sin pérdida de los datos existentes.

## Versión Android

- Room schema 5.
- versionCode 5
- versionName 0.8.0
