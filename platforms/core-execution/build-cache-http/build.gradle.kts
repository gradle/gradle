plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Implementation for interacting with HTTP build caches"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)

    api(libs.httpcore)
    api(libs.inject)
    api(libs.jsr305)

    api(projects.baseServices)
    api(projects.buildCacheSpi)
    api(projects.coreApi)
    api(projects.resourcesHttp)

    implementation(projects.core)
    implementation(projects.logging)
    implementation(projects.resources)

    implementation(libs.commonsHttpclient)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(libs.servletApi)

    integTestImplementation(projects.enterpriseOperations)
    integTestImplementation(testFixtures(projects.buildCache))
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Uses application plugin.")
    }
}
