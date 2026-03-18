// tag::apply[]
plugins {
    `kotlin-dsl`
}
// end::apply[]

// tag::repositories-and-dependencies[]
repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("com.github.spotbugs:com.github.spotbugs.gradle.plugin:6.4.8")
    testImplementation("junit:junit:4.13")
}
// end::repositories-and-dependencies[]
