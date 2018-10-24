// Need to have at least one configuration declared, otherwise the rules are never evaluated
val conf by configurations.creating

// tag::module_to_project_substitution[]
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.utils:api")).apply {
            with(project(":api"))
            because("we work with the unreleased development version")
        }
        substitute(module("org.utils:util:2.5")).with(project(":util"))
    }
}
// end::module_to_project_substitution[]
// tag::project_to_module_substitution[]
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(project(":api")).apply {
            with(module("org.utils:api:1.3"))
            because("we use a stable version of org.utils:api")
        }
    }
}
// end::project_to_module_substitution[]
