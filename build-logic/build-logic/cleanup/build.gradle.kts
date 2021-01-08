plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation(project(":module-identity"))
}
