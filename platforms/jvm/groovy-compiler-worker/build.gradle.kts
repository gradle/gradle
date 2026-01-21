plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.internalInstrumentationApi)
    api(projects.javaCompilerWorker)
    api(projects.languageJvm)
    api(projects.platformBase)
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
