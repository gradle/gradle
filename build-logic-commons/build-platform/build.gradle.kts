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
                if (module.group == "org.jetbrains.kotlin") {
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

        api("org.codehaus.plexus:plexus-utils") {
            because("Versions below 3.6.1 and 4.0.2 are vulnerable to CVE-2025-67030")
            version {
                reject("[3.0,3.6.1)", "[4.0,4.0.3)")
            }
        }
    }
}
