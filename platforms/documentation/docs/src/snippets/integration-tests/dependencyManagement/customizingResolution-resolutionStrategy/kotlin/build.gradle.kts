import org.gradle.api.artifacts.component.ModuleComponentSelector

// tag::resolve-rules[]
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.example:old-library"))
            .because("Our license only allows use of version 1")
            .using(module("com.example:new-library:1.0.0"))
    }
}
// end::resolve-rules[]

// tag::custom-versioning-scheme[]
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        all {
            val req = requested as? ModuleComponentSelector ?: return@all
            if (req.version == "default") {
                val version = findDefaultVersionInCatalog(req.group, req.module)
                useTarget("${req.group}:${req.module}:${version.version}", version.because)
            }
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
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.software:some-library:1.2"))
            .because("fixes critical bug in 1.2")
            .using(module("org.software:some-library:1.2.1"))
    }
}
// end::denying_version[]

// tag::module_substitution[]
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        all {
            val req = requested as? ModuleComponentSelector ?: return@all
            if (req.module == "groovy-all") {
                useTarget("${req.group}:groovy:${req.version}", """prefer "groovy" over "groovy-all"""")
            }
            if (req.module == "log4j") {
                useTarget("org.slf4j:log4j-over-slf4j:1.7.10", """prefer "log4j-over-slf4j" 1.7.10 over any version of "log4j"""")
            }
        }
    }
}
// end::module_substitution[]
