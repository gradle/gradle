# kotlin-friendly-groovy-plugin 

Demonstrates a Gradle plugin implemented in Groovy while still exposing a Kotlin DSL friendly API.

Check out the [consuming build script](./consumer/build.gradle.kts#L6) to see the friendly API in action.

Check out the [Groovy plugin source code](./plugin/src/main/groovy/my/DocumentationPlugin.groovy#L5) for tips on how to achieve the same effect. 


## Project structure

This sample contains two projects:

 1. [plugin](./plugin) - a Gradle plugin implemented in Groovy,
 2. [consumer](./consumer) - a build that uses the Gradle plugin above.

## Running

Run with:

    ./gradlew consumer

This will build and publish the Gradle `plugin` locally then run the `books` task contributed by the plugin in the `consumer` project. 
