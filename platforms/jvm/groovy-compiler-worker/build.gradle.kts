plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains logic for compiling groovy source files. May execute within a separate worker process."

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.internalInstrumentationApi)
    api(projects.javaCompilerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.problemsApi)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.groovyLoader)
    implementation(projects.stdlibJavaExtensions)
    implementation(libs.asm)
    implementation(libs.groovy)
    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))
}
