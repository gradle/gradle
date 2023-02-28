plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A code quality stubs"

dependencies {
    compileOnly(project(":core-api"))
}
