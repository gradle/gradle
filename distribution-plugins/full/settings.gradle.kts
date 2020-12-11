pluginManagement {
    includeBuild("../../build-logic/build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
}

includeBuild("../publishing")
includeBuild("../native") { name = "native-build" } // clashes with project ':native'
