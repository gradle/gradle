pluginManagement {
    includeBuild("../../build-logic/build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
}

includeBuild("../native")
includeBuild("../publishing")
