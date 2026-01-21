plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.fileCollections)
    api(projects.languageJvm)
    api(projects.platformBase)
    api(projects.problemsApi)
    api(projects.stdlibJavaExtensions)
    api("com.google.guava:guava")
    api("javax.inject:javax.inject")
    api("org.jspecify:jspecify")
    api("org.slf4j:slf4j-api")

    implementation(projects.concurrent)
    implementation(projects.core)
    implementation(projects.loggingApi)
    implementation(projects.problemsRendering)
}
