// tag::publishing[]
plugins {
    // end::publishing[]
    distribution
// tag::publishing[]
    `maven-publish`
}
// end::publishing[]

group = "org.gradle.sample"
version = "1.0"

distributions {
    main {
        contents {
            from("src")
        }
    }
    create("custom") {
        contents {
            from("src")
        }
    }
}

// tag::publishing[]

publishing {
    publications {
        create<MavenPublication>("myDistribution") {
            artifact(tasks.distZip)
            artifact(tasks["customDistTar"])
        }
    }
}
// end::publishing[]

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
