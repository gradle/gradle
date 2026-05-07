plugins {
    `java-platform`
}

group = "gradlebuild"

description = "Provides a platform that constrains versions of external dependencies used by Gradle"

dependencies {
    constraints {
        val distributionDependencies = versionCatalogs.named("buildLibs")
        distributionDependencies.libraryAliases.forEach { alias ->
            api(distributionDependencies.findLibrary(alias).get().map { module ->
                if (module.group == "org.jetbrains.kotlin") {
                    module.copy().apply {
                        version {
                            strictly(embeddedKotlinVersion)
                        }
                    }
                } else {
                    module
                }
            })
        }
    }
}
