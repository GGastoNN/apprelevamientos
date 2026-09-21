# Grupo IDEA - Relevamientos — Especificación funcional V0.5

## Principio de diseño

La aplicación debe poder operarse con una mano, sin conexión y con la menor cantidad de pantallas posible. El flujo principal está optimizado para una visita de obra: entrar al sector, abrir el vano, medir, fotografiar, acotar y continuar.

## Jerarquía

Obra → Espacio/Sector → Vano/Carpintería → Evidencias.

La obra mantiene además una Bitácora transversal que registra hechos, cambios, incidencias y decisiones.

## Datos de obra

- Nombre.
- Cliente.
- Dirección.
- Responsable/contacto.
- Estado: En curso / Pausada / Finalizada.
- Observaciones generales.
- Fecha de creación y última actualización.

## Datos del espacio

- Nombre.
- Planta/nivel.
- Sector/fachada.
- Observaciones.

## Datos del vano

- Código.
- Tipo.
- Ancho y alto.
- Antepecho.
- Diagonales.
- Espesor de muro.
- Profundidad.
- Holguras izquierda, derecha, superior e inferior.
- Plomo.
- Nivel.
- Escuadra.
- Estado de piso.
- Estado de revoque.
- Premarco.
- Sentido/condición de apertura.
- Interferencias.
- Estado operativo.
- Observaciones.

## Evidencia fotográfica

- Archivo original en almacenamiento privado.
- Fecha/hora.
- Comentario individual.
- Detecciones automáticas de candidatos de vano.
- Cotas manuales con dos puntos, etiqueta y valor real.
- Imagen principal por vano.
- Eliminación controlada.

## Bitácora

Registra automáticamente creación de obra, espacios, vanos, fotos, mediciones y cambios de estado. Permite además cargar notas manuales clasificadas como Información, Alerta o Decisión.

## PDF

El informe incluye:

- Portada profesional.
- Datos generales de la obra.
- Resumen cuantitativo.
- Espacios y sectores.
- Ficha técnica de cada vano.
- Todas las fotografías disponibles.
- Cotas dibujadas sobre las fotografías.
- Comentarios de evidencia.
- Bitácora cronológica completa.
- Aclaración técnica sobre medición fotográfica.

## Seguridad y persistencia

- Datos offline en Room.
- Fotografías e informes dentro del almacenamiento privado de la app.
- Compartir PDF mediante FileProvider.
- Sin permisos de almacenamiento público.
- Migración de base V1→V2 para preservar los relevamientos de la versión inicial.


## Giro de fotografías — V0.6

- Cada evidencia puede rotarse de a 90° desde el editor.
- El giro es no destructivo: no vuelve a comprimir ni reemplaza el JPG original.
- La orientación queda guardada en Room y se aplica a miniaturas, editor y PDF.
- Detecciones automáticas y cotas se transforman con la fotografía, manteniendo su posición correcta.
