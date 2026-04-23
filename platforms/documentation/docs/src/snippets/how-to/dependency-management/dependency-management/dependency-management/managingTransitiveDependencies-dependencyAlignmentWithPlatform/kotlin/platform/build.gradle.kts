// tag::platform[]
// tag::convention[]
plugins {
    `java-platform`
// end::platform[]
    id("myproject.publishing-conventions")
// tag::platform[]
}
// end::convention[]

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

// tag::convention[]

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            from(components["javaPlatform"])
        }
    }
}
// tag::convention[]
