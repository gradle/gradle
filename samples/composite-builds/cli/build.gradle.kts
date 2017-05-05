
plugins {
    application
}

group = "org.gradle.script.kotlin.samples.composite-builds"
version = "1.0"

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile("org.gradle.script.kotlin.samples.composite-builds:core:1.0")
}
