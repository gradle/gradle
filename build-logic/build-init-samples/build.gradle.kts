plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

dependencies {
    implementation("org.gradle.guides:gradle-guides-plugin")
    implementation("org.asciidoctor:asciidoctor-gradle-plugin") {
        because("This is a transitive dependency of 'gradle-guides-plugin' not declared there")
    }
}
