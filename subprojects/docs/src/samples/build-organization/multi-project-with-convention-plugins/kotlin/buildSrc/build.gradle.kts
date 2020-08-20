// tag::apply[]
plugins {
    `kotlin-dsl`
// end::apply[]
    `maven-publish`
// tag::apply[]
}
// end::apply[]

group = "com.example.conventions"
version = "1.0"

// tag::repositories-and-dependencies[]
repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("gradle.plugin.com.github.spotbugs.snom:spotbugs-gradle-plugin:4.0.5")
    testImplementation("junit:junit:4.13")
}
// end::repositories-and-dependencies[]
