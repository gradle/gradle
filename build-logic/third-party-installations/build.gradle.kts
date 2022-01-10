plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins for installing third party software"

dependencies {
    implementation(project(":basics"))
}
