/**
 * This project provides the "platform" for the Gradle distribution.
 * We want the versions that are packaged in the distribution to be used everywhere (e.g. in all test scenarios)
 * Hence, we lock the versions down here for all other subprojects.
 *
 * Note:
 * We use strictly here because we do not have any better means to do this at the moment.
 * Ideally we wound be able to say "lock down all the versions of the dependencies resolved for the distribution"
 */
plugins {
    id("gradlebuild.platform")
}

description = "Provides a platform dependency to align all distribution versions"

// For the junit-bom
javaPlatform.allowDependencies()

dependencies {
    api(platform(providedLibs.junitBom))

    constraints {
        val distributionDependencies = versionCatalogs.named("libs")
        distributionDependencies.libraryAliases.forEach { alias ->
            api(distributionDependencies.findLibrary(alias).get())
        }
        val providedDependencies = versionCatalogs.named("providedLibs")
        providedDependencies.libraryAliases.forEach { alias ->
            api(providedDependencies.findLibrary(alias).get())
        }
        api("org.codehaus.plexus:plexus-utils") {
            because("Versions below 3.6.1 and 4.0.3 are vulnerable to CVE-2025-67030")
            version {
                reject("[3.0,3.6.1)", "[4.0,4.0.3)")
            }
        }
    }
}

