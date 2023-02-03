val implementation by configurations.creating

// tag::delegateClosureOf[]
dependencies {
    implementation("group:artifact:1.2.3") {
        artifact(delegateClosureOf<DependencyArtifact> {
            // configuration for the artifact
            name = "artifact-name"
        })
    }
}
// end::delegateClosureOf[]
