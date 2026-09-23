# Grupo IDEA - Relevamientos — Especificación funcional V1.0

## Principio

La aplicación prioriza velocidad de campo y trazabilidad: cada acción frecuente debe resolverse con pocos toques, funcionar sin Internet y mantener la evidencia original disponible.

## Jerarquía

Obra → Espacio/Sector → Vano/Carpintería → Evidencias.

La Bitácora de obra registra decisiones, incidencias, fotografías, altas y cambios relevantes.

## Vano

Incluye identificación, tipo, medidas, diagonales, espesores, profundidad, holguras, plomo, nivel, escuadra, piso, revoque, premarco, apertura, interferencias, estado y observaciones.

### Duplicado rápido

La opción `Duplicar vano` copia la geometría y el tipo de una ficha existente para acelerar series repetitivas. El nuevo vano obtiene código automático y reinicia controles/estado para evitar asumir que dos ubicaciones fueron verificadas de la misma forma.

## Evidencia fotográfica

Cada evidencia contiene:

- JPG original privado;
- fecha/hora;
- comentario;
- clasificación General / Inicial / Incidencia / Corrección / Final;
- rotación no destructiva;
- detecciones automáticas;
- cotas;
- marcas gráficas;
- indicador de foto principal.

### Herramientas de anotación

- Cota: dos puntos + etiqueta + valor.
- Flecha: señalización dirigida.
- Rectángulo: delimitar una zona.
- Círculo/elipse: resaltar un punto o defecto.
- Texto: pin con observación.

Las coordenadas se almacenan normalizadas respecto de la imagen y se transforman correctamente al girar la fotografía.

## Marca corporativa

Configuración persistente mediante preferencias locales:

- nombre de empresa;
- logo personalizado importado desde el dispositivo;
- sello activado/desactivado;
- fecha/hora opcional;
- obra opcional;
- sector opcional;
- vano opcional.

La foto original nunca se sobreescribe. Para compartir o incluir en PDF se genera una representación con sello y anotaciones.

## Incidencias

Desde cada vano se puede registrar rápidamente una incidencia con título, detalle y severidad Información / Alerta / Decisión. El evento queda asociado al vano y se incorpora a la bitácora del proyecto.

## Exportación

- PDF compacto por obra: portada con referencias del edificio y una ficha por vano.
- Antes de generar el PDF se elige qué fotografía incluir en cada vano; la selección inicial es siempre la evidencia más reciente.
- Es posible omitir una fotografía para reducir todavía más el peso del informe.
- La ficha PDF integra medidas, controles, observaciones y fotografía en una misma hoja.
- Las imágenes del PDF se limitan a una resolución adecuada para impresión A4 para evitar archivos innecesariamente pesados.
- JPG técnico individual para compartir desde Android.
- Ambas salidas respetan rotación, anotaciones y marca configurada.

## Persistencia

Room schema 5 con migraciones 1→2, 2→3, 3→4 y 4→5. La migración 4→5 agrega las fotografías generales de referencia del edificio sin eliminar datos previos.


## Transferencia portable de obras

- Desde una obra se puede exportar únicamente ese proyecto.
- Desde el dashboard se pueden exportar todas las obras en un único archivo `*.gidea`.
- El archivo es versionado y contiene la jerarquía completa de Room, la bitácora, fotografías generales y evidencias.
- Las fotografías se guardan como originales y las cotas/anotaciones se transfieren como metadatos, evitando duplicar una versión renderizada de cada imagen.
- La importación reasigna IDs locales y reconstruye relaciones entre obra, espacios, vanos, evidencias y eventos.
- Importar nunca sobrescribe obras existentes: crea copias independientes en el dispositivo destino.
- El formato queda preparado para evolucionar hacia sincronización remota; los IDs locales no se consideran identificadores globales.
