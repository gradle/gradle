plugins {
    `java-library`
}

// tag::use_classifier[]
dependencies {
    implementation(variantOf(libs.my.lib) { classifier("linux-x86_64") })
}
// end::use_classifier[]

// tag::use_classifier_sources[]
dependencies {
    // Depend on the sources JAR as an artifact
    implementation(variantOf(libs.my.lib) { classifier("sources") })

    // Depend on the Javadoc JAR as an artifact
    implementation(variantOf(libs.my.lib) { classifier("javadoc") })
}
// end::use_classifier_sources[]

// tag::use_artifact_type[]
dependencies {
    implementation(libs.my.lib) {
        // A String-notation dependency with a type or classifier resolves non-transitively by default.
        // The catalog accessor does not get this automatic behavior, so set it explicitly to match.
        isTransitive = false
        artifact {
            name = "my-lib"
            type = "aar"
        }
    }
}
// end::use_artifact_type[]
