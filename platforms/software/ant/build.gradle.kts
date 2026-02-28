plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API for Gradle's Ant integration"

strictCompile {
    ignoreRawTypes() // raw types in BuilderSupport overrides (AntBuilderDelegate) and Groovy internals (BasicAntBuilder)
}

dependencies {
    api(projects.antApi)
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)
    api(libs.groovy)                      // Groovy core types used in public API
    api(libs.ant)                         // AntLoggingAdapter implements BuildLogger

    implementation(projects.coreApi)      // Logger, LogLevel
    implementation(projects.logging)      // LogLevelMapping
    implementation(projects.loggingApi)   // LogLevelMapping (transitive)
    implementation(projects.modelCore)    // DynamicObject, DynamicObjectUtil (AntBuilderDelegate)
    implementation(libs.groovyAnt)        // AntBuilderDelegate wraps groovy.ant.AntBuilder
    implementation(libs.groovyXml)        // XmlParser (AntBuilderDelegate)
    implementation(libs.guava)

    compileOnly(libs.jetbrainsAnnotations)
}
