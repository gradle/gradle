
plugins {
    application
}

group = "org.gradle.kotlin.dsl.samples.composite-builds"
version = "1.0"

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile("org.gradle.kotlin.dsl.samples.composite-builds:core:1.0")
}
