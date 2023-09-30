plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to create and configure integration, cross-version and distribution tests"

dependencies {
    implementation("gradlebuild:basics")
    implementation(project(":cleanup"))
    implementation(project(":dependency-modules"))
    implementation(project(":module-identity"))

    testImplementation("junit:junit")
}
