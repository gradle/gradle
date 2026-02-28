plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of Gradle's Ant integration"

strictCompile {
    ignoreRawTypes() // raw types in DefaultIsolatedAntBuilder (Vector) and Groovy-based APIs
}

dependencies {
    api(projects.ant)                      // IsolatedAntBuilder, AntBuilderFactory, AntBuilderDelegate
    api(projects.antApi)                   // AntBuilder
    api(projects.core)                     // ClassPathRegistry, ModuleRegistry, ClassLoaderFactory, etc.
    api(projects.coreApi)                  // Project, Task, Transformer
    api(projects.baseServices)             // UncheckedException, Stoppable
    api(projects.buildProcessServices)
    api(projects.classloaders)             // CachingClassLoader, FilteringClassLoader, etc.
    api(projects.concurrent)
    api(projects.serviceProvider)
    api(libs.ant)
    api(libs.groovy)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.loggingApi)
    implementation(projects.modelCore)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.groovyLoader)  // GroovySystemLoader, GroovySystemLoaderFactory
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    compileOnly(libs.jetbrainsAnnotations)
    compileOnly(projects.internalInstrumentationApi)

    testImplementation(testFixtures(projects.core))   // AbstractProjectBuilderSpec, TestUtil
    testImplementation(projects.internalTesting)       // TestNameTestDirectoryProvider
    testImplementation(testFixtures(projects.logging)) // ConfigureLogging, TestOutputEventListener

    testRuntimeOnly(projects.distributionsCore) {
        because("Required by ProjectBuilder to find gradle-plugins.properties")
    }

    integTestImplementation(libs.log4jToSlf4j)   // TestAntTask: org.apache.log4j
    integTestImplementation(libs.jclToSlf4j)      // TestAntTask: org.apache.commons.logging

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("AntBuilder relies on groovy-loader which ships with the JVM distribution")
    }
}
