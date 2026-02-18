plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.core)
    api(projects.coreApi)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)
    api("org.jspecify:jspecify")

    implementation("com.google.guava:guava")
}
