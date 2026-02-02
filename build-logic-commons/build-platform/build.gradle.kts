plugins {
    `java-platform`
}

group = "gradlebuild"

description = "Provides a platform that constrains versions of external dependencies used by Gradle"

// To try out newer kotlin versions
val kotlinVersion = providers.gradleProperty("buildKotlinVersion")
    .getOrElse(embeddedKotlinVersion)

dependencies {
    constraints {
        val distributionDependencies = versionCatalogs.named("buildLibs")
        distributionDependencies.libraryAliases.forEach { alias ->
            api(distributionDependencies.findLibrary(alias).get().map { module ->
                if (module.version == "kotlin-stub") {
                    module.copy().apply {
                        version {
                            strictly(kotlinVersion)
                        }
                    }
                } else {
                    module
                }
            })
        }
    }
}
