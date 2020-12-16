pluginManagement {
    includeBuild("build-logic-settings")
    includeBuild("build-logic/build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
    id("gradlebuild.base.allprojects")
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
