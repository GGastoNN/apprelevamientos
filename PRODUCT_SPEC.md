# Grupo IDEA - Relevamientos — Especificación funcional V0.7

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

- PDF completo por obra.
- JPG técnico individual para compartir desde Android.
- Ambas salidas respetan rotación, anotaciones y marca configurada.

## Persistencia

Room schema 4 con migraciones 1→2, 2→3 y 3→4. La migración 3→4 agrega la etapa fotográfica sin eliminar datos previos.
