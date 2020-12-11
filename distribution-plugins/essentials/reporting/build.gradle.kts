plugins {
    id("gradlebuild.distribution.api-java")
}

val implementationResources: Configuration by configurations.creating

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jatl)

    implementationResources("jquery:jquery.min:3.5.1@js")

    testImplementation("org.gradle:process-services")
    testImplementation("org.gradle:base-services-groovy")
    testImplementation(libs.jsoup)
    testImplementation(testFixtures("org.gradle:core"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-jvm") {
        because("BuildDashboard has specific support for JVM plugins (CodeNarc, JaCoCo)")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreParameterizedVarargType() // [unchecked] Possible heap pollution from parameterized vararg type: GenerateBuildDashboard.aggregate()
}

classycle {
    excludePatterns.add("org/gradle/api/reporting/internal/**")
}

val reportResources = tasks.register<Copy>("reportResources") {
    from(implementationResources)
    into(layout.buildDirectory.file("generated-resources/report-resources/org/gradle/reporting"))
}

sourceSets.main {
    output.dir(reportResources.map { it.destinationDir.parentFile.parentFile.parentFile })
}
