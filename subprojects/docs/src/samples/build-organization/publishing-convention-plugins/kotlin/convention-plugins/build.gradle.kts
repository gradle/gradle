// tag::apply[]
// tag::publish[]
plugins {
    `kotlin-dsl`
// end::apply[]
    `maven-publish`
// tag::apply[]
}
// end::apply[]

group = "com.myorg.conventions"
version = "1.0"

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
}

tasks.named("publish") {
    dependsOn("check")
}
// end::publish[]

// tag::repositories-and-dependencies[]
repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("gradle.plugin.com.github.spotbugs.snom:spotbugs-gradle-plugin:4.0.5")
    testImplementation("junit:junit:4.13")
}
// end::repositories-and-dependencies[]
