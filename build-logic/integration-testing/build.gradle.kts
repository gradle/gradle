plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":basics"))
    implementation(project(":cleanup"))
    implementation(project(":dependency-modules"))
    implementation(project(":module-identity"))

    testImplementation("junit:junit")
}
