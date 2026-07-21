plugins {
    `java-library`
}

// tag::use_classifier[]
dependencies {
    implementation(variantOf(libs.my.lib) { classifier("test-fixtures") })
}
// end::use_classifier[]

// tag::use_classifier_sources[]
dependencies {
    // Add the library's source code for IDE integration
    implementation(variantOf(libs.my.lib) { classifier("sources") })
    
    // Add the library's Javadoc for IDE integration
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
