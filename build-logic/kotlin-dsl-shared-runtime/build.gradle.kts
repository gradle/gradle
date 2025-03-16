plugins {
    id("gradlebuild.kotlin-shared-runtime")
}

description = "Provides Kotlin DSL code that is shared between build-logic and runtime"

dependencies {
    compileOnly(platform("gradlebuild:build-platform"))
    compileOnly(kotlin("stdlib"))
    compileOnly("org.ow2.asm:asm-tree")
    compileOnly("com.google.code.findbugs:jsr305")
    compileOnly("org.jspecify:jspecify")
}
