plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-project")
}

description = "Contains declarations for instrumentation of plugins. Adds interceptors, bytecode upgrades etc."

errorprone {
    disabledChecks.addAll(
        "InvalidBlockTag", // 1 occurrences
    )
}

dependencies {
    // All dependencies should be compileOnly, since this project is added also to worker classpath, so we don't pollute it.
    // If we need some dependency also at runtime we need to build a separate classpath and add it to :launcher project or :distributions-core project directly.
    compileOnly(project(":core"))
    compileOnly(projects.javaLanguageExtensions)
    compileOnly(project(":base-services"))
    compileOnly(project(":core-api"))
    compileOnly(project(":model-core"))
    compileOnly(project(":reporting"))
    compileOnly(libs.groovy)
    // We keep code-quality here, since we would need to separate Groovy and Java sourceset to keep incremental compilation
    compileOnly(project(":code-quality"))
}

