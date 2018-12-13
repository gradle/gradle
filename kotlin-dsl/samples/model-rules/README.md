# model-rules 

Demonstrates how to write model rules in Kotlin and how to apply them from a `.gradle.kts` file.

Check out the [rule source](./buildSrc/src/main/kotlin/my/model_rules.kt#L11) written in Kotlin.

Check out how it is applied in the [build.gradle.kts](./build.gradle.kts) file. 

Run with:

    ./gradlew hello

This will apply the model rules and run the created `hello` task. 
