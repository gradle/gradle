import org.gradle.api.publish.internal.component.ConfigurationVariantDetailsInternal
import org.gradle.kotlin.dsl.assign

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Internal interfaces and implementations for input normalization"

dependencies {
    api(projects.normalizationApi)
    api(projects.baseServices)
    api(projects.snapshots)
    api(projects.hashing)
    api(projects.files)
    api(projects.serviceProvider)

    api(libs.jspecify)

    implementation(libs.guava)

    testImplementation(projects.internalTesting)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

listOf(configurations["apiElements"], configurations["runtimeElements"]).forEach {
    (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(it) {
        this as ConfigurationVariantDetailsInternal
        this.dependencyMapping {
            publishResolvedCoordinates = true
        }
    }
}
