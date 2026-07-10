import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
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

// tag::attribute-compatibility[]
// Define the compatibility rule class
class TargetJvmVersionCompatibilityRule : AttributeCompatibilityRule<Int> {
    // Implement the execute method which will check compatibility
    override fun execute(details: CompatibilityCheckDetails<Int>) {
        // Switch case to check the consumer value for supported Java versions
        when (details.consumerValue) {
            8, 11 -> details.compatible()  // Compatible with Java 8 and 11
            else -> details.incompatible()
        }
    }
}

// Register the compatibility rule within the dependencies block
dependencies {
    attributesSchema {
        // Add the compatibility rule for the TargetJvmVersion attribute
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) {
            // Add the defined compatibility rule to this attribute
            compatibilityRules.add(TargetJvmVersionCompatibilityRule::class.java)
        }
    }
}
// end::attribute-compatibility[]
