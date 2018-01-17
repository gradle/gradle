import org.jetbrains.kotlin.gradle.plugin.KaptAnnotationProcessorOptions
import org.jetbrains.kotlin.gradle.plugin.KaptJavacOptionsDelegate

plugins {
    application
    kotlin("jvm") version "1.2.20"
    kotlin("kapt") version "1.2.20"
}

application {
    mainClassName = "samples.HelloAutoValueKt"
}

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
    testCompile("junit:junit:4.12")

    // Use AutoValue to check that annotation processing works
    // https://github.com/google/auto/tree/master/value
    compileOnly("com.google.auto.value:auto-value:1.5")
    // Kapt configuration for an annotation processor
    kapt("com.google.auto.value:auto-value:1.5")
}

kapt {
    // Type safe accessors for Kapt DSL
    correctErrorTypes = true

    // Example of Javac Options configuration
    javacOptions(delegateClosureOf<KaptJavacOptionsDelegate> {
        option("SomeJavacOption", "OptionValue")
    })

    // Kapt Arguments configuration
    arguments(delegateClosureOf<KaptAnnotationProcessorOptions> {
        arg("SomeKaptArgument", "ArgumentValue")
    })
}
