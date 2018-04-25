# precompiled-script-plugin

A sample demonstrating _precompiled script plugins_.

A precompiled script plugin is a Kotlin script compiled as part of a regular Kotlin source-set and distributed
in the usual way, as java class files packaged in some library, meant to be consumed as a binary
Gradle plugin.

The Gradle plugin id by which the precompiled script can be referenced is derived from its name
and optional package declaration.

Thus, the script `src/main/kotlin/code-quality.gradle.kts` is exposed as the `code-quality`
plugin (assuming it has no package declaration) whereas the script
`src/main/kotlin/my/code-quality.gradle.kts` is exposed as the `my.code-quality`
plugin, again assuming it has the matching package declaration.

The sample is comprised of two builds:

 1. [plugin](./plugin) a Gradle plugin implemented as a precompiled script. 
 2. [consumer](./consumer) a build that uses the Gradle plugin above.

Run with:

    ./gradlew consumer

This will build and publish the Gradle `plugin` locally ; and then run the task contributed by this plugin in the `consumer` build. 
