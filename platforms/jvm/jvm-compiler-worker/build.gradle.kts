plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.internalInstrumentationApi)
    api(projects.stdlibJavaExtensions)
    api("javax.inject:javax.inject")
    api("org.jspecify:jspecify")

    implementation(projects.classloaders)
    implementation("com.google.guava:guava")
    implementation("org.apache.commons:commons-lang3")
}
