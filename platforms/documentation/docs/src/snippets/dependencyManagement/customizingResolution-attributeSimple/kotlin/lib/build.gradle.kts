import org.gradle.api.attributes.java.TargetJvmVersion

// tag::attributes[]
plugins {
    id("java-library")
}

// end::attributes[]

repositories {
    mavenCentral()
}

// tag::attributes[]
configurations {
    named("apiElements") {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
        }
    }
}
// end::attributes[]

// tag::custom-attributes[]
// Define a custom attribute
val myAttribute = Attribute.of("com.example.my-attribute", String::class.java)

configurations {
    create("myConfig") {
        // Set custom attribute
        attributes {
            attribute(myAttribute, "special-value")
        }
    }
}

dependencies {
    // Apply the custom attribute to a dependency
    add("myConfig","com.google.guava:guava:31.1-jre") {
        attributes {
            attribute(myAttribute, "special-value")
        }
    }
}
// end::custom-attributes[]
