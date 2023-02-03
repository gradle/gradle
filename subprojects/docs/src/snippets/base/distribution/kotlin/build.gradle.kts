// tag::use-plugin[]
plugins {
    distribution
}
// end::use-plugin[]

// tag::configure-distribution[]
distributions {
    main {
        distributionBaseName.set("someName")
        distributionClassifier.set("classifier")
        contents {
            from("src/readme")
        }
    }
}
// end::configure-distribution[]

// tag::custom-distribution[]
distributions {
    create("custom") {
        // configure custom distribution
    }
}
// end::custom-distribution[]
