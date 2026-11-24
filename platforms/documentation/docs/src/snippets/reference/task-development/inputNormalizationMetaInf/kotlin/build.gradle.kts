plugins {
    java
}

// tag::ignore-metainf-attribute[]
normalization {
    runtimeClasspath {
        metaInf {
            ignoreAttribute("Implementation-Version")
        }
    }
}
// end::ignore-metainf-attribute[]

// tag::ignore-metainf-properties[]
normalization {
    runtimeClasspath {
        metaInf {
            ignoreProperty("app.version")
        }
    }
}
// end::ignore-metainf-properties[]

// tag::ignore-metainf-manifest[]
normalization {
    runtimeClasspath {
        metaInf {
            ignoreManifest()
        }
    }
}
// end::ignore-metainf-manifest[]

// tag::ignore-metainf-completely[]
normalization {
    runtimeClasspath {
        metaInf {
            ignoreCompletely()
        }
    }
}
// end::ignore-metainf-completely[]
