import build.*

plugins {
    `public-kotlin-dsl-module`
}

base {
    archivesBaseName = "gradle-kotlin-dsl-tooling-builders"
}

dependencies {
    compileOnly(gradleApiWithParameterNames())

    compile(project(":provider"))

    testCompile(project(":test-fixtures"))
}

// -- Testing ----------------------------------------------------------
tasks {
    test {
        dependsOn(":customInstallation")
    }
}

withParallelTests()
