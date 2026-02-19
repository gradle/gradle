plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementations for TAPI IDE models"

dependencies {
    api(projects.baseServices)
    api(projects.clientServices)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(libs.guava)
}

gradleModule {
    computedRuntimes {
    }
}
