plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.internalInstrumentationApi)
    api(projects.javaCompilerWorker)
    api(projects.languageJava)
    api(projects.languageJvm)
    api(projects.platformBase)
    api(projects.problemsApi)
    api("com.google.guava:guava")
    api("javax.inject:javax.inject")
    api("org.apache.groovy:groovy")
    api("org.jspecify:jspecify")
    api("org.ow2.asm:asm")

    implementation(projects.concurrent)
    implementation(projects.groovyLoader)
    implementation(projects.stdlibJavaExtensions)
}
