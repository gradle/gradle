plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Report type classes and plugins for reporting (build dashboard, report container)"

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.reportRendering)
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.logging)

    implementation(libs.guava)
    implementation(libs.jatl)

    testImplementation(projects.processServices)
    testImplementation(projects.baseServicesGroovy)
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.jacoco))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("BuildDashboard has specific support for JVM plugins (CodeNarc, JaCoCo)")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/internal/**")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
