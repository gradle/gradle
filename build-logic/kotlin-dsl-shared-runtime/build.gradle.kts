plugins {
    id("gradlebuild.kotlin-shared-runtime")
}

description = "Provides Kotlin DSL code that is shared between build-logic and runtime"

group = "org.gradle.kotlin-dsl-shared-runtime"

dependencies {
    compileOnly(platform("gradlebuild:build-platform"))
    compileOnly("org.ow2.asm:asm-tree")
    compileOnly("com.google.code.findbugs:jsr305")
}
