plugins {
    `java-platform`
    `maven-publish`
}

// tag::project-constraints[]
dependencies {
    constraints {
        api(project(":core"))
        api(project(":lib"))
    }
}
// end::project-constraints[]

// tag::publishing[]
publishing {
    publications {
        create<MavenPublication>("myPlatform") {
            from(components["javaPlatform"])
        }
    }
}
// end::publishing[]
