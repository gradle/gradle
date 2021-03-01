pluginManagement {
    includeBuild("build-logic/build-logic-base")
    // TODO to allow RC version of 'test-distribution' until is https://github.com/gradle/gradle/issues/15416 fixed
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://repo.gradle.org/gradle/enterprise-libs-release-candidates-local") }
    }
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
    // TODO directly applied here due to https://github.com/gradle/gradle/issues/15416
    id("com.gradle.enterprise.test-distribution") version "2.0-rc-5"
}

rootProject.name = "gradle"

includeBuild("distribution-plugins/native")
includeBuild("distribution-core")
includeBuild("distribution-plugins/essentials")
includeBuild("distribution-plugins/basics")
includeBuild("distribution-plugins/jvm")
includeBuild("distribution-plugins/publishing")
includeBuild("distribution-plugins/full")

includeBuild("portal-plugins")

includeBuild("testing/fixtures")
includeBuild("testing/end-to-end-tests")
includeBuild("testing/reports")

includeBuild("documentation")
