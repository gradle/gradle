plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation(project(":module-identity"))

    implementation("com.gradle.publish:plugin-publish-plugin")
}
