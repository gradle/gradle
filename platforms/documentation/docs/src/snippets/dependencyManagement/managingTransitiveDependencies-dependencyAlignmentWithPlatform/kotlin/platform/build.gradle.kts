// tag::platform[]
plugins {
    `java-platform`
// end::platform[]
    id("myproject.publishing-conventions")
// tag::platform[]
}

dependencies {
    // The platform declares constraints on all components that
    // require alignment
    constraints {
        api(project(":core"))
        api(project(":lib"))
        api(project(":utils"))
    }
}
// end::platform[]

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            from(components["javaPlatform"])
        }
    }
}
