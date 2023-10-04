plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides a plugin to generate samples using internal build init APIs"

dependencies {
    implementation("gradlebuild:basics")
    implementation("org.gradle.guides:gradle-guides-plugin")
    implementation("org.asciidoctor:asciidoctor-gradle-jvm") {
        because("This is a transitive dependency of 'gradle-guides-plugin' not declared there")
    }
}
