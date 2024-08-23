import gradlebuild.basics.googleApisJs

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Report type classes and plugins for reporting (build dashboard, report container)"

errorprone {
    disabledChecks.addAll(
        "EqualsUnsafeCast", // 1 occurrences
    )
}

val implementationResources: Configuration by configurations.creating

repositories {
    googleApisJs()
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.reportRendering)
    api(projects.stdlibJavaExtensions)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.fileCollections)
    implementation(projects.logging)
    implementation(projects.modelCore)
    implementation(projects.serviceLookup)

    implementation(libs.guava)
    implementation(libs.jatl)

    implementationResources("jquery:jquery.min:3.5.1@js")

    testImplementation(projects.processServices)
    testImplementation(projects.baseServicesGroovy)
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(projects.core))

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

val reportResources = tasks.register<Copy>("reportResources") {
    from(implementationResources)
    into(layout.buildDirectory.file("generated-resources/report-resources/org/gradle/reporting"))
}

sourceSets.main {
    output.dir(reportResources.map { it.destinationDir.parentFile.parentFile.parentFile })
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
