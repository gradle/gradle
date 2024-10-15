// tag::java-configuration-example[]
// declare a "configuration" named "implementation"
val implementation by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}

// tag::declare-configuration[]
dependencies {
    // add a project dependency to the implementation configuration
    implementation(project(":lib"))
}
// end::declare-configuration[]

// tag::concrete-classpath[]
configurations {
    // declare a resolvable configuration that is going to resolve the compile classpath of the application
    resolvable("compileClasspath") {
        //isCanBeConsumed = false
        //isCanBeDeclared = false
        extendsFrom(implementation)
    }
// end::concrete-classpath[]
    // declare a resolvable configuration that is going to resolve the runtime classpath of the application
    resolvable("runtimeClasspath") {
        //isCanBeConsumed = false
        //isCanBeDeclared = false
        extendsFrom(implementation)
    }
// tag::concrete-classpath[]
}
// end::concrete-classpath[]

// tag::setup-configurations[]
configurations {
    // a consumable configuration meant for consumers that need the API of this component
    consumable("exposedApi") {
        //isCanBeResolved = false
        //isCanBeDeclared = false
        extendsFrom(implementation)
    }
// end::setup-configurations[]
    // a consumable configuration meant for consumers that need the implementation of this component
    consumable("exposedRuntime") {
        //isCanBeResolved = false
        //isCanBeDeclared = false
        extendsFrom(implementation)
    }
// tag::setup-configurations[]
}
// end::setup-configurations[]
// end::java-configuration-example[]

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
