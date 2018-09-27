// tag::publishing[]
plugins {
    // end::publishing[]
    distribution
// tag::publishing[]
    `ivy-publish`
}
// end::publishing[]

group = "org.gradle.sample"
version = "1.0"

distributions {
    getByName("main") {
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
        create<IvyPublication>("myDistribution") {
            artifact(tasks["distZip"])
            artifact(tasks["customDistTar"])
        }
    }
}
// end::publishing[]

publishing {
    repositories {
        ivy {
            url = uri("$buildDir/repo")
        }
    }
}
