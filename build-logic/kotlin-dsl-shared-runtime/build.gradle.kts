plugins {
    id("gradlebuild.kotlin-shared-runtime")
}

description = "Provides Kotlin DSL code that is shared between build-logic and runtime"

dependencies {
    compileOnly(platform("gradlebuild:build-platform"))
    compileOnly(libs.kotlinStdlib)
    compileOnly(libs.asmTree)
    compileOnly(libs.jsr305)
    compileOnly(libs.jspecify)
}
