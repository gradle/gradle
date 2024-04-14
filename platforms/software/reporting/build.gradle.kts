import gradlebuild.basics.googleApisJs

plugins {
    id("gradlebuild.distribution.api-java")
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
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":model-core"))

    implementation(libs.guava)
    implementation(libs.jatl)

    implementationResources("jquery:jquery.min:3.5.1@js")

    testImplementation(project(":process-services"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.jsoup)
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
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
