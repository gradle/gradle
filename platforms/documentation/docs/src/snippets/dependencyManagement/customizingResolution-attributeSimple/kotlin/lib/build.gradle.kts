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
// Define the attribute you want to apply compatibility rules to (JavaLanguageVersion in this case)
val javaLanguageVersionAttribute = Attribute.of("JavaLanguageVersion", Integer::class.java)

// Register the compatibility rule for the JavaLanguageVersion attribute
dependencies {
    configurations {
        named("myConfig") {
            attributes {
                // Define which attribute to apply compatibility rules to
                attribute(javaLanguageVersionAttribute, 11) // Java version 11 for example
            }
        }
    }
}

// Register a compatibility rule using `attributeMatchingStrategy`
configurations.all {
    resolutionStrategy.attributeMatchingStrategy(javaLanguageVersionAttribute) { version ->
        when (version) {
            8 -> CompatibilityResult.compatible() // Compatible with Java 8
            11 -> CompatibilityResult.compatible() // Compatible with Java 11
            else -> CompatibilityResult.incompatible("Unsupported Java version")
        }
    }
}
// end::attribute-compatibility[]
