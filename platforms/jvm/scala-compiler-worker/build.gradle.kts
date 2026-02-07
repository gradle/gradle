plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implements compilation of Scala source files. May execute within a separate worker process."

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.hashing)
    api(projects.internalInstrumentationApi)
    api(projects.javaCompilerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.scopedPersistentCache)
    api(projects.stdlibJavaExtensions)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.loggingApi)
    implementation(projects.persistentCache)
    implementation(projects.time)
    implementation(libs.guava)

    compileOnly(providedLibs.zinc) {
        // Because not needed and was vulnerable
        exclude(module="log4j-core")
        exclude(module="log4j-api")
    }
}
