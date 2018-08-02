import build.*

plugins {
    id("public-kotlin-dsl-module")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-tooling-builders"
}

dependencies {
    compileOnly(gradleApi())

    compile(project(":provider"))

    testCompile(project(":test-fixtures"))
}

// -- Testing ----------------------------------------------------------
tasks.named("test").configure {
    dependsOn(rootProject.tasks.named("customInstallation"))
}

withParallelTests()
