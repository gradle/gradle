plugins {
    id("java-library")
}

// tag::configuration-lazy-add-dependency[]
configurations {
    implementation {
        dependencies.addLater(project.provider {
            val dependencyNotation = conditionalLogic()
            if (dependencyNotation != null) {
                project.dependencies.create(dependencyNotation)
            } else {
                null
            }
        })
    }
}
// end::configuration-lazy-add-dependency[]

fun conditionalLogic(): String? {
    return "org:foo:1.0"
}

// tag::preferred-version-constraints[]
dependencies {
    implementation("org:foo")

    // Can indiscriminately be added by build logic
    constraints {
        implementation("org:foo:1.0") {
            version {
                // Applied to org:foo if no other version is specified
                prefer("1.0")
            }
        }
    }
}
// end::preferred-version-constraints[]

