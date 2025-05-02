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
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.2.1")
    testImplementation("junit:junit:4.13")
}
// end::repositories-and-dependencies[]
