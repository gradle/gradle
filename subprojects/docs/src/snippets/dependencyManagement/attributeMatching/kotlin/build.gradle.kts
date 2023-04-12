plugins {
    `java-library`
}

// tag::declare-configuration[]
// declare a "configuration" named "someConfiguration"
val someConfiguration by configurations.creating

dependencies {
    // add a project dependency to the "someConfiguration" configuration
    someConfiguration(project(":lib"))
}
// end::declare-configuration[]

// tag::concrete-classpath[]
configurations {
    // declare a configuration that is going to resolve the compile classpath of the application
    compileClasspath {
        extendsFrom(someConfiguration)
    }

    // declare a configuration that is going to resolve the runtime classpath of the application
    runtimeClasspath {
        extendsFrom(someConfiguration)
    }
}
// end::concrete-classpath[]

// tag::setup-configurations[]
configurations {
    // A configuration meant for consumers that need the API of this component
    create("exposedApi") {
        // This configuration is an "outgoing" configuration, it's not meant to be resolved
        isCanBeResolved = false
        // As an outgoing configuration, explain that consumers may want to consume it
        assert(isCanBeConsumed)
    }
    // A configuration meant for consumers that need the implementation of this component
    create("exposedRuntime") {
        isCanBeResolved = false
        assert(isCanBeConsumed)
    }
}
// end::setup-configurations[]

// tag::define_attribute[]
// An attribute of type `String`
val myAttribute = Attribute.of("my.attribute.name", String::class.java)
// An attribute of type `Usage`
val myUsage = Attribute.of("my.usage.attribute", Usage::class.java)
// end::define_attribute[]

// tag::register-attributes[]
dependencies.attributesSchema {
    // registers this attribute to the attributes schema
    attribute(myAttribute)
    attribute(myUsage)
}
// end::register-attributes[]

// tag::attributes-on-configurations[]
configurations {
    create("myConfiguration") {
        attributes {
            attribute(myAttribute, "my-value")
        }
    }
}
// end::attributes-on-configurations[]

// tag::named-attributes[]
configurations {
    "myConfiguration" {
        attributes {
            attribute(myUsage, project.objects.named(Usage::class.java, "my-value"))
        }
    }
}
// end::named-attributes[]
