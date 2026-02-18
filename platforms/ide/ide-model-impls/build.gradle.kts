plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(libs.guava)
}
