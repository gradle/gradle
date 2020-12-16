pluginManagement {
    includeBuild("../build-logic/build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
}

includeBuild("../testing/fixtures")

// This is currently required for testing, but introduces a 'circle' between all builds
includeBuild("../distribution-plugins/native") { name = "native-plugins" } // we need to rename, because of the clash with subproject 'native'
includeBuild("../distribution-plugins/full")
