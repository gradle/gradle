import org.gradle.api.attributes.java.TargetJvmVersion

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
configurations {
    named("apiElements") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11) // Allows fallback
        }
    }
    named("runtimeElements") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11) // Allows fallback
        }
    }
}
// end::attribute-strategy[]
