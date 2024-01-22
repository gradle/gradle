plugins {
    id("java-library")
}

// tag::ignore-build-info-properties[]
normalization {
    runtimeClasspath {
        ignore("build-info.properties")
    }
}
// end::ignore-build-info-properties[]
