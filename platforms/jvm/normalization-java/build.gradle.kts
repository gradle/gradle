import org.gradle.api.publish.internal.component.ConfigurationVariantDetailsInternal

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "API extraction for Java"

errorprone {
    disabledChecks.addAll(
        "NonApiType", // 1 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
    )
}

dependencies {
    api(projects.hashing)
    api(projects.files)
    api(projects.snapshots)

    api(libs.jsr305)
    api(libs.guava)
    api("org.gradle:java-api-extractor")

    implementation(projects.functional)

    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(projects.baseServices)
    testImplementation(projects.internalTesting)
    testImplementation(testFixtures(projects.snapshots))
}

// TODO Put a comment here about what this does
listOf(configurations["apiElements"], configurations["runtimeElements"]).forEach {
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(it) {
        this as ConfigurationVariantDetailsInternal
        this.dependencyMapping {
            publishResolvedCoordinates = true
        }
    }
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
