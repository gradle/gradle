plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains declarations for instrumentation of plugins. Adds interceptors, bytecode upgrades etc."

dependencies {
    // All dependencies should be compileOnly, since this project is added also to worker classpath, so we don't pollute it.
    // If we need some dependency also at runtime we need to build a separate classpath and add it to :launcher project or :distributions-core project directly.
    compileOnly(projects.core)
    compileOnly(projects.stdlibJavaExtensions)
    compileOnly(projects.baseServices)
    compileOnly(projects.coreApi)
    compileOnly(projects.modelCore)
    compileOnly(projects.reporting)
    compileOnly(libs.groovy)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
