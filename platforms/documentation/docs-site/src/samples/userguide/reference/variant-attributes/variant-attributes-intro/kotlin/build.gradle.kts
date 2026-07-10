import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

plugins {
    id("java-library")
}

interface TargetConfiguration : Named {
    companion object {
        val TARGET_ATTRIBUTE: Attribute<TargetConfiguration> = Attribute.of(TargetConfiguration::class.java)
    }
}

// tag::bundling-attribute[]
configurations {
    compileClasspath {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}
// end::bundling-attribute[]

// tag::target-attribute[]
configurations {
    compileClasspath {
        attributes {
            // tag::target-attribute-single[]
            attribute(TargetConfiguration.TARGET_ATTRIBUTE, objects.named("debug"))
            // end::target-attribute-single[]
        }
    }
}
// end::target-attribute[]
