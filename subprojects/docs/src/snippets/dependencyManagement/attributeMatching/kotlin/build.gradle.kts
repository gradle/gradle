// tag::declare-configuration[]
configurations {
    // Declare two dependency scope configurations, named `implementation` and `runtimeOnly`
    create("implementation") {
        // This configuration is only meant to declare dependencies.
        // It should not be resolved or consumed
        isCanBeResolved = false
        isCanBeConsumed = false
    }
    create("runtimeOnly") {
        isCanBeResolved = false
        isCanBeConsumed = false
    }
}

val implementation by configurations.existing
val runtimeOnly by configurations.existing
dependencies {
    // Declare that our project's API depends on artifacts from the `lib` project
    implementation(project(":lib"))
    // Declare that our project's implementation depends on artifacts from the `anotherLib` project
    runtimeOnly(project(":anotherLib"))
}
// end::declare-configuration[]

// tag::resolvable-configurations[]
configurations {
    // Declare two resolvable configurations, named `compileClasspath` and `runtimeClasspath`
    create("compileClasspath") {
        // This configuration is not meant to be consumed and
        // should not allow dependencies to be declared on it
        isCanBeConsumed = false
        isCanBeDeclared = false
        extendsFrom(implementation)
    }
    create("runtimeClasspath") {
        isCanBeConsumed = false
        isCanBeDeclared = false
        extendsFrom(implementation)
        extendsFrom(runtimeOnly)
    }
}
// end::resolvable-configurations[]

// tag::consumable-configurations[]
configurations {
    // Declare two consumable configurations, named `apiElements` and `runtimeElements`
    create("apiElements") {
        // This configuration is not meant to be resolved and
        // should not allow dependencies to be declared on it
        isCanBeResolved = false
        isCanBeDeclared = false
        extendsFrom(implementation)
    }
    create("runtimeElements") {
        isCanBeResolved = false
        isCanBeDeclared = false
        extendsFrom(implementation)
        extendsFrom(runtimeOnly)
    }
}
// end::consumable-configurations[]

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
    named("apiElements") {
        attributes {
            attribute(myAttribute, "my-value")
        }
    }
}
// end::attributes-on-configurations[]

// tag::named-attributes[]
configurations {
    named("runtimeElements") {
        attributes {
            attribute(myUsage, project.objects.named(Usage::class.java, "my-value"))
        }
    }
}
// end::named-attributes[]
