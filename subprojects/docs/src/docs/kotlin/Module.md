# Module Kotlin DSL Reference

Gradle’s Kotlin DSL provides an alternative syntax to the traditional Groovy DSL with an enhanced editing experience in supported IDEs, with superior content assist, refactoring, documentation, and more.
For an introduction see the <a href="../userguide/kotlin_dsl.html">Kotlin DSL Primer</a>.

The Kotlin DSL is implemented on top of Gradle’s Java API.
Many of the objects, functions and properties you use in your build scripts come from the Gradle API and the APIs of the applied plugins.
This reference covers both the Kotlin DSL and the Java API, but it doesn't include functionality provided by external plugins.

The main package of the Kotlin DSL is <a href="./-kotlin%20-d-s-l%20-reference/org.gradle.kotlin.dsl/index.html">org.gradle.kotlin.dsl</a>, which is also an implicit import in all Kotlin build scripts.
The other important package the Kotlin DSL is <a href="./-kotlin%20-d-s-l%20-reference/org.gradle.kotlin.dsl.precompile/index.html">org.gradle.kotlin.precompile</a>.
The rest of the packages belong to the Java API.
