plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public API for Gradle's Ant integration"

dependencies {
    api(libs.groovyAnt)                        // extends groovy.ant.AntBuilder
    api(projects.baseServices)                 // Transformer<> used in importBuild signatures

    implementation(libs.groovy)

    compileOnly(projects.internalInstrumentationApi)  // @NotToBeMigratedToLazy annotation — compile-time only
    compileOnly(libs.jspecify)
    compileOnly(libs.jetbrainsAnnotations)
}
