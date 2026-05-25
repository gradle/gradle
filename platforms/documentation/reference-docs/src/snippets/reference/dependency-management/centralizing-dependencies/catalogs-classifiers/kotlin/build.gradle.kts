plugins {
    `java-library`
}

// tag::use_classifier[]
dependencies {
    implementation(variantOf(libs.my.lib) { classifier("test-fixtures") })
}
// end::use_classifier[]

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
