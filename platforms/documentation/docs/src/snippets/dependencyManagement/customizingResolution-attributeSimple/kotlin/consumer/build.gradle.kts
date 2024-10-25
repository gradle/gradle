// tag::attributes[]
plugins {
    id("application")
}

// end::attributes[]

repositories {
    mavenCentral()
}

// tag::attributes[]
dependencies {
    implementation(project(":lib")) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
        }
    }
}
// end::attributes[]

// tag::attribute-strategy[]
configurations.all {
    // Define compatibility rules for attribute matching
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11) // Allows fallback
    }
}
// end::attribute-strategy[]
