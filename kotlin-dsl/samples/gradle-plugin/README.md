# gradle-plugin

A multi-build sample with:

 1. [plugin](./plugin) a Gradle plugin implemented in Kotlin and taking advantage of the `kotlin-dsl` plugin,
 2. [consumer](./consumer) a build that uses the Gradle plugin above.

Run with:

    ./gradlew consumer

This will build and publish the Gradle `plugin` locally ; and then run the task contributed by this plugin in the `consumer` build. 
