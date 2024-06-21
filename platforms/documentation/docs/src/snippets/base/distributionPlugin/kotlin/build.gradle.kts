plugins {
    distribution
}

// tag::configure-distribution[]
distributions {
    main {
        distributionBaseName = "someName"
        contents {
            into("bin/config") {
                from("config")
            }
            into("lib/samples") {
                from("samples")
            }
        }
    }
}
// end::configure-distribution[]
