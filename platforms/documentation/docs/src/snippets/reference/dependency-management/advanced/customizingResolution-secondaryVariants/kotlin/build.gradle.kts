// tag::lib[]
plugins {
    id("java-library")
}

group = "com.gradle"
version = "1.2.3"
// end::lib[]

// tag::add[]
// Create a new secondary variant on the existing 'apiElements' configuration.
// This variant will be available during artifact selection (not graph resolution).
configurations.apiElements.get().outgoing.variants.create("apiElementVariant") {
    attributes {
        // Override the 'Usage' attribute to distinguish this variant from the default 'java-api'.
        // This allows consumers requesting 'custom-variant' usage to select this artifact set.
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("custom-variant"))
    }
}
// end::add[]

// tag::custom[]
// Define custom attributes for variant identification
val fooAttribute = Attribute.of("com.example.foo", String::class.java)
val fooVariantAttribute = Attribute.of("com.example.fooVariant", String::class.java)

// Create a consumable configuration named 'fooFiles'
// This configuration serves as the container for outgoing variants
val fooFiles = configurations.create("fooFiles") {
    isCanBeDeclared = false
    isCanBeResolved = false
    isCanBeConsumed = true
    attributes {
        // Base attribute for the configuration
        attribute(fooAttribute, "main")
    }
}

// Define the first variant of 'fooFiles'
// This variant inherits all attributes from the parent configuration and adds a distinguishing attribute to identify the variant
fooFiles.outgoing.variants.create("fooFilesVariant1") {
    attributes {
        attribute(fooVariantAttribute, "variant1")
    }
}

// Define a second variant of 'fooFiles'
// This one overrides the inherited 'fooAttribute' value and sets a different 'fooVariantAttribute' value
fooFiles.outgoing.variants.create("fooFilesVariant2") {
    attributes {
        attribute(fooAttribute, "secondary") // Overrides inherited attribute
        attribute(fooVariantAttribute, "variant2")
    }
}
// end::custom[]
