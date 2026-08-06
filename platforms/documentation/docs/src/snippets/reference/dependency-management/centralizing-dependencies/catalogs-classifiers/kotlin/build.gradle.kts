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
        artifact {
            name = "my-lib"
            type = "aar"
        }
    }
}
// end::use_artifact_type[]
