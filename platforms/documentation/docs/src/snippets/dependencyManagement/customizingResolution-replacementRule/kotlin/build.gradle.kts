plugins {
    java
}

// TODO the API should probably be updated to avoid that cast
// tag::module_replacement_declaration[]
dependencies {
    modules {
        module("com.google.collections:google-collections") {
            replacedBy("com.google.guava:guava", "google-collections is now part of Guava")
        }
    }
}
// end::module_replacement_declaration[]
