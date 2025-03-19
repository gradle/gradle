plugins {
    `java-library`
    `maven-publish`
}

group = "com.acme"
version = "1.0"

// tag::declare-outgoing-capabilities[]
configurations {
    apiElements {
        outgoing {
            capability("com.acme:my-library:1.0")
            capability("com.other:module:1.1")
        }
    }
    runtimeElements {
        outgoing {
            capability("com.acme:my-library:1.0")
            capability("com.other:module:1.1")
        }
    }
}
// end::declare-outgoing-capabilities[]

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    // tag::ignore-pom-warnings[]
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            suppressPomMetadataWarningsFor("runtimeElements")
        }
    }
    // end::ignore-pom-warnings[]
}

