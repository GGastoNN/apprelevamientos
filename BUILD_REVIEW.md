# Revisión de build — 1.1.1

## Hallazgo corregido

`PhotoRenderer.kt` tenía un conflicto entre el argumento `style: PersonalizationSettings` y la propiedad `Paint.style` usada dentro de `Paint(...).apply { ... }`. Kotlin resolvía `style = ...` contra el argumento externo, provocando un error de compilación. Se reemplazó por `this.style = ...` en las tres instancias afectadas.

## Verificaciones realizadas

- Integridad del ZIP/proyecto.
- Parseo de recursos XML y Manifest.
- Revisión de `build.gradle.kts`, `settings.gradle.kts` y workflow Android.
- Compilación sintáctica/semántica aislada de `PersonalizationStore`, `AppTheme` y `PhotoRenderer` con stubs de Android/Compose para detectar conflictos de Kotlin.
- Compatibilidad declarada AGP 8.7.x ↔ Gradle 8.9 y JDK 17.

## Limitación de esta revisión

El entorno de revisión no incluye Android SDK ni una instalación de Gradle, por lo que no fue posible ejecutar aquí `:app:assembleDebug`. El workflow `.github/workflows/android.yml` queda listo para realizar el build completo en GitHub Actions.

## Recomendación de mantenimiento

El proyecto usa Kotlin 2.0.21/KSP con Room 2.6.1. Room 2.7.x incorporó soporte explícito para Kotlin 2.0/KSP2; conviene evaluar esa actualización en una versión posterior y probar la migración en CI antes de adoptarla.
