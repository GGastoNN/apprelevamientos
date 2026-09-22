# V0.7 Professional Field

## Marca corporativa en evidencias

- Sello visual configurable en cada fotografía.
- Nombre de empresa editable; valor inicial: Grupo IDEA.
- Importación de logo PNG/JPG desde el dispositivo, copiado al almacenamiento privado de la app.
- El JPG original se conserva intacto.
- La marca se aplica en editor, miniaturas, imágenes compartidas y PDF.
- El sello puede incluir obra, sector, vano, etapa y fecha/hora.

## Herramientas fotográficas

- Cotas con dos puntos y valor real.
- Flechas.
- Rectángulos.
- Círculos.
- Texto/pines de observación.
- Deshacer última cota y última marca.
- Giro 90° izquierda/derecha, manteniendo la geometría de las anotaciones.
- Compartir una copia JPG técnica con sello y anotaciones, sin alterar la foto original.

## Flujo diario

- Clasificación de fotos: General, Inicial, Incidencia, Corrección y Final.
- Registro rápido de incidencias directamente desde el vano.
- Duplicado de vano para series repetitivas: copia geometría/tipo y reinicia estado y controles para obligar una nueva verificación.
- La bitácora registra los nuevos eventos.

## Persistencia

- Room schema 4.
- Migración 3→4: agrega `phase` a evidencias con valor `GENERAL`.
- Compatible con datos provenientes de V0.1/V0.5/V0.6 mediante la cadena de migraciones existente.

## Versión Android

- versionCode 4
- versionName 0.7.0
