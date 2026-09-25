# V1.2.0 — interfaz dinámica y flujo rápido

## Velocidad de relevamiento

- Nuevo **Modo rápido** dentro de cada vano. Oculta temporalmente campos secundarios y deja visibles las tareas esenciales de campo.
- Botón fijo **Guardar y siguiente** para recorrer vanos consecutivos sin volver al listado.
- Navegación directa **Anterior / Siguiente** entre vanos del mismo espacio.
- Indicador de autoguardado: `Guardando…` / `Guardado ✓`.
- El autoguardado reduce el retardo a 700 ms y se fuerza antes de abrir la cámara o cambiar de vano.
- Alta de vano más rápida con tipos predefinidos: Ventana, Puerta, Paño fijo u Otro.

## Mejoras visuales

- Nuevo panel resumen en la pantalla principal con cantidad y estado de las obras.
- Bordes y tarjetas con geometría más moderna y consistente.
- Animaciones suaves al cambiar contenido y progreso.
- Barra de avance por espacio/sector.
- Progreso del vano basado en cuatro pasos esenciales: medidas principales, controles, evidencia fotográfica y estado final.
- Tarjetas de vano con indicadores compactos de medidas, controles e interferencias.

## Búsqueda y filtros

- Búsqueda dentro de cada espacio por código, tipo u observaciones.
- Filtros rápidos por Pendiente, Verificar, Relevado y Aprobado.
- Mensajes específicos cuando un filtro no devuelve resultados.

## Build

- `versionCode 11`
- `versionName 1.2.0`
- Se agrega `androidx.compose.animation:animation` para transiciones y animaciones de tamaño.
