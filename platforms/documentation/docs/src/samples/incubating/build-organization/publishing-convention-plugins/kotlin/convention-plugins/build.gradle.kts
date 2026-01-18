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
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

tasks.publish {
    dependsOn("check")
}
// end::publish[]


testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnit()
        }
    }
}

// tag::repositories-and-dependencies[]
repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("com.github.spotbugs:com.github.spotbugs.gradle.plugin:6.4.8")
}
// end::repositories-and-dependencies[]
