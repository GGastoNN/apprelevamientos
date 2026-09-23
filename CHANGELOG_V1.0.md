# RelevaMetal v1.0.1 — transferencia completa de obras

## Archivo portable de datos

- Se puede exportar una obra completa desde la pantalla de la obra con **Datos > Exportar obra**.
- Desde la pantalla principal se puede exportar **todas las obras** en un solo archivo.
- El archivo generado usa la extensión propia `*.gidea`, manteniendo internamente un contenedor comprimido y versionado.
- La importación sigue validando el manifiesto interno, por lo que no depende solamente del nombre o la extensión del archivo.

## Qué se conserva

La transferencia incluye:

- datos de la obra, cliente, dirección, responsable, notas y estado;
- espacios/sectores;
- todos los vanos y sus medidas;
- controles técnicos, estados e interferencias;
- bitácora completa;
- fotos generales del edificio;
- todas las fotografías de los vanos;
- cotas, textos, flechas, rectángulos, círculos y demás anotaciones de cada foto;
- datos de detección, giro, fase, foto principal y fechas.

Las fotografías originales se guardan una sola vez junto con sus metadatos de anotación. Al importar, la aplicación vuelve a renderizar las cotas sobre la foto, evitando duplicar imágenes y tamaño innecesariamente.

## Importación

- En la pantalla principal: **Datos > Importar archivo de datos**.
- Las obras importadas se agregan como nuevas obras y no reemplazan ni modifican las existentes.
- Los identificadores internos se reasignan en el dispositivo de destino para evitar conflictos.
- Se validan las imágenes y las rutas del archivo antes de incorporarlas.

## Preparación para sincronización futura

El respaldo incorpora versión de formato e identificador de archivo, dejando una base clara para migrar más adelante a sincronización multiusuario en la nube sin depender de los IDs locales de Room.
