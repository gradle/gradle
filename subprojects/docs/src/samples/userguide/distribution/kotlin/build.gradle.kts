// tag::use-plugin[]
// tag::publish-distribution[]
plugins {
    // end::publish-distribution[]
    distribution
// end::use-plugin[]
// tag::publish-distribution[]
    maven
// tag::use-plugin[]
}
// end::use-plugin[]
// end::publish-distribution[]

// tag::configure-distribution[]
distributions {
    main {
        baseName = "someName"
        contents {
            from("src/readme")
        }
    }
}
// end::configure-distribution[]

// tag::publish-distribution[]

tasks.named<Upload>("uploadArchives") {
    repositories.withGroovyBuilder {
        "mavenDeployer" {
            "repository"("url" to "file://some/repo")
        }
    }
}
// end::publish-distribution[]

// tag::custom-distribution[]
distributions {
    create("custom") {
        // configure custom distribution
    }
}
// end::custom-distribution[]
