plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin that cleans up after executing tests"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")
}
