pluginManagement {
    includeBuild("build-logic-settings")
    includeBuild("build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
    id("gradlebuild.base.allprojects")
}

includeBuild("subprojects")

rootProject.name = "gradle"
