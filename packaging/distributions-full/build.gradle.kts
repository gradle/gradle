import gradlebuild.basics.BuildEnvironmentExtension

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.verify-build-environment")
    id("gradlebuild.install")
}

description = "The collector project for the entirety of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    publicAbiOnly(projects.publicApi) {
        capabilities {
            requireCapability("org.gradle.experimental:gradle-public-api-legacy")
        }
    }

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsPublishing))
    pluginsRuntimeOnly(platform(projects.distributionsJvm))
    pluginsRuntimeOnly(platform(projects.distributionsNative))

    pluginsRuntimeOnly(projects.pluginDevelopment)
    pluginsRuntimeOnly(projects.buildConfiguration)
    pluginsRuntimeOnly(projects.buildInit)
    pluginsRuntimeOnly(projects.wrapperMain) {
        because("Need to include the wrapper source in the distribution")
    }
    pluginsRuntimeOnly(projects.buildProfile)
    pluginsRuntimeOnly(projects.antlr)
    pluginsRuntimeOnly(projects.enterprise)
    pluginsRuntimeOnly(projects.unitTestFixtures)

    pluginsRuntimeOnly(libs.xdclGradlePlugin) {
        // The XDCL Gradle API is a Gradle module (:xdcl-api repackages the org.xdcl build's jar);
        // the org.xdcl build's own copy must not ride into the image as a second copy of the classes.
        exclude(group = "org.xdcl", module = "xdcl-gradle-api")
    }

    // The shared schema foundation the built-in ecosystems import. Also PUBLISHED as
    // org.gradle:gradle-xdcl-common-ecosystem and served by the embedded repo (repo/); bundled so its
    // facade classes load parent-first from the distribution.
    pluginsRuntimeOnly(projects.xdclCommonEcosystem)
    // The plugin-development ecosystem — the declarative face of authoring an XDCL plugin; its
    // reaction drives the real java-library/java-gradle-plugin/xdcl-gradle-plugin machinery.
    pluginsRuntimeOnly(projects.xdclPluginDevelopment)
    pluginsRuntimeOnly(projects.xdclPluginDevelopmentPlugin)

    // The embedded Maven repository (repo/ in the image): the published ecosystem libraries at the
    // distribution version — the full offline-resolution closure a consumer build's settings
    // classpath needs when the XDCL provider injects built-in ecosystems into dependency resolution.
    // (The XDCL Gradle API they extend is Gradle API, shipped in lib/ by :xdcl-api and referenced by
    // no published metadata, so the repository has nothing to serve for it.)
    distributionRepositoryOnly(projects.xdclCommonEcosystem)
    distributionRepositoryOnly(projects.xdclPluginDevelopment)
}

// The manifest auto-derives module names from `gradle-<name>-<version>.jar` file names, which
// covers the ecosystem carriers; the org.xdcl codegen plugin's jar doesn't follow that naming, so
// its MODULE name is registered explicitly — that puts it on the distribution's plugins
// classloader, which is what lets `plugins { id "…" }` resolve it like a builtin. (Entries are
// module-registry names, not plugin ids — DefaultPluginModuleRegistry silently ignores anything
// that doesn't resolve as a module.)
tasks.named<gradlebuild.packaging.tasks.PluginsManifest>("implementationPluginsManifest") {
    additionalPlugins.add("xdcl-gradle-plugin")
}

// This is required for the separate promotion build and should be adjusted there in the future
val buildEnvironmentExtension = extensions.getByType(BuildEnvironmentExtension::class)
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(buildEnvironmentExtension.rootProjectBuildDir.dir("distributions"))
}
