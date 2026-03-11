kotlinSharedRuntime {
    description = "Provides Kotlin DSL code that is shared between build-logic and runtime"

    dependencies {
        compileOnly(platform("gradlebuild:build-platform"))
        compileOnly(catalog("libs.kotlinStdlib"))
        compileOnly(catalog("libs.asmTree"))
        compileOnly(catalog("libs.jsr305"))
        compileOnly(catalog("libs.jspecify"))
    }
}
