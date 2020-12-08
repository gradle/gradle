import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    includeBuild("build-logic-settings")
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local") }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.5.2")
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin").version("0.7.2")
    id("gradlebuild.base.allprojects")
    // Keep version with `build-logic/build-platform/buildSrc.gradle.kts` in sync
    id("com.gradle.enterprise.test-distribution").version("2.0")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")
includeBuild("subprojects")

rootProject.name = "gradle"

FeaturePreviews.Feature.values().forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}
