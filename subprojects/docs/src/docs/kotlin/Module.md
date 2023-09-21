# Module Kotlin DSL Reference for Gradle

# Kotlin DSL Reference for Gradle

Gradle’s Kotlin DSL provides an enhanced editing experience in supported IDEs, with superior content assist, refactoring, documentation, and more.
For an introduction see the <a href="../userguide/kotlin_dsl.html">Kotlin DSL Primer</a>.

The Kotlin DSL is implemented on top of Gradle’s Java API.
Many of the objects, functions and properties you use in your build scripts come from the Gradle API and the APIs of the applied plugins.
This reference covers both the Kotlin DSL and the Java API, but it doesn't include functionality provided by external plugins.

The main package of the Kotlin DSL is <a href="./gradle/org.gradle.kotlin.dsl/index.html">org.gradle.kotlin.dsl</a>.
All members of this package are implicitly imported and readily available in `.gradle.kts` scripts in addition to the Java API <a href="../userguide/writing_build_scripts.html#script-default-imports">default imports</a>.

# Package org.gradle.kotlin.dsl

The `org.gradle.kotlin.dsl` package contains the Gradle Kotlin DSL public API.

All members of this package are implicitly imported and readily available in `.gradle.kts` scripts in addition to the Gradle Java API <a href="../../../userguide/writing_build_scripts.html#script-default-imports">default imports</a>.
