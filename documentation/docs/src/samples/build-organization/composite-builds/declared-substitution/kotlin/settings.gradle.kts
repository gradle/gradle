rootProject.name = "declared-substitution"

include("app")

// tag::composite_substitution[]
includeBuild("anonymous-library") {
    dependencySubstitution {
        substitute(module("org.sample:number-utils")).with(project(":"))
    }
}
// end::composite_substitution[]
