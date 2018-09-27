plugins {
    java
}

// tag::locking-all[]
dependencyLocking {
    lockAllConfigurations()
}
// end::locking-all[]

// tag::resolve-all[]
task("resolveAndLockAll") {
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks)
    }
    doLast {
        configurations.filter {
            // Add any custom filtering on the configurations to be resolved
            it.isCanBeResolved
        }.forEach { it.resolve() }
    }
}
// end::resolve-all[]
