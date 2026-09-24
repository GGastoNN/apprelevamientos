# RelevaMetal 1.1.1

## Corrección de build

- Corregido un conflicto de nombres en `PhotoRenderer`: el parámetro de personalización `style` ocultaba la propiedad `Paint.style` dentro de bloques `apply`.
- Se ajustaron las tres asignaciones de estilo de `Paint` a `this.style`, evitando errores de compilación `val cannot be reassigned` / `type mismatch`.
- Se verificó sintaxis Kotlin de los módulos modificados mediante compilación con stubs y se validaron XML/ZIP del proyecto.
- Se mantiene la configuración de build: AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, JDK 17, compileSdk/targetSdk 35.
