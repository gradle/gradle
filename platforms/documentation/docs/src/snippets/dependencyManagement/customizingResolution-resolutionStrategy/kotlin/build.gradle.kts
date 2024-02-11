// tag::custom-versioning-scheme[]
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.version == "default") {
            val version = findDefaultVersionInCatalog(requested.group, requested.name)
            useVersion(version.version)
            because(version.because)
        }
    }
}

data class DefaultVersion(val version: String, val because: String)

fun findDefaultVersionInCatalog(group: String, name: String): DefaultVersion {
    //some custom logic that resolves the default version into a specific version
    return DefaultVersion(version = "1.0", because = "tested by QA")
}
// end::custom-versioning-scheme[]

// tag::denying_version[]
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.software" && requested.name == "some-library" && requested.version == "1.2") {
            useVersion("1.2.1")
            because("fixes critical bug in 1.2")
        }
    }
}
// end::denying_version[]

// tag::module_substitution[]
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.name == "groovy-all") {
            useTarget(mapOf("group" to requested.group, "name" to "groovy", "version" to requested.version))
            because("""prefer "groovy" over "groovy-all"""")
        }
        if (requested.name == "log4j") {
            useTarget("org.slf4j:log4j-over-slf4j:1.7.10")
            because("""prefer "log4j-over-slf4j" 1.7.10 over any version of "log4j"""")
        }
    }
}
// end::module_substitution[]

