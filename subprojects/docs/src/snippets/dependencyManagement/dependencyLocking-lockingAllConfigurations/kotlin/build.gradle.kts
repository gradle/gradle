plugins {
    java
}

// tag::locking-all[]
dependencyLocking {
    lockAllConfigurations()
}
// end::locking-all[]

// tag::resolve-all[]
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
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
