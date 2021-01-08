pluginManagement {
    includeBuild("../build-logic-base")
}

plugins {
    id("gradlebuild.settings-plugins")
    id("gradlebuild.repositories")
}

include("code-quality")
include("code-quality-rules")
include("gradle-plugin")
