plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation("com.google.code.gson:gson")
}
