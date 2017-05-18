import build.*

plugins {
    base
}

base {
    archivesBaseName = "gradle-script-kotlin-compiler-plugin"
}

dependencies {
    compile(kotlinModule("compiler-embeddable"))
    testCompile(project(":test-fixtures"))
}

// -- Testing ----------------------------------------------------------
val customInstallation by rootProject.tasks
tasks {
    "test" {
        dependsOn(customInstallation)
    }
}

withParallelTests()
