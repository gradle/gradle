// tag::full-platform[]
plugins {
    `java-platform`
}

// tag::define-platform[]
dependencies {
    constraints {
        // Platform declares some versions of libraries used in subprojects
        api("commons-httpclient:commons-httpclient:3.1")
        api("org.apache.commons:commons-lang3:3.8.1")
    }
}
// end::define-platform[]
// end::full-platform[]
