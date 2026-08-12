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

    pluginsRuntimeOnly(libs.xdclGradlePlugin)

    // The shared schema foundation the JVM ecosystem (and future ecosystems) import. Also PUBLISHED as
    // org.gradle:gradle-xdcl-common-ecosystem and served by the embedded repo (repo/); bundled so its
    // facade classes load parent-first from the distribution.
    pluginsRuntimeOnly(projects.xdclCommonEcosystem)
    // Shared imperative carrier glue (DependencyScopes/Repositories) — distribution-only, not published.
    pluginsRuntimeOnly(projects.xdclEcosystemSupport)
    pluginsRuntimeOnly(projects.xdclJvmEcosystem)
    pluginsRuntimeOnly(projects.xdclJvmEcosystemPlugin)
    pluginsRuntimeOnly(projects.xdclJvmCheckstyle)
    pluginsRuntimeOnly(projects.xdclJvmCheckstylePlugin)
    pluginsRuntimeOnly(projects.xdclJvmInstrumentation)
    pluginsRuntimeOnly(projects.xdclJvmInstrumentationPlugin)
    // The Groovy ecosystem — a sibling of the JVM one (shares the common schema, not the JVM schema).
    pluginsRuntimeOnly(projects.xdclGroovyEcosystem)
    pluginsRuntimeOnly(projects.xdclGroovyEcosystemPlugin)
    // The plugin-development ecosystem — the declarative face of authoring an XDCL plugin; its
    // reaction drives the real java-library/java-gradle-plugin/xdcl-gradle-plugin machinery.
    pluginsRuntimeOnly(projects.xdclPluginDevelopment)
    pluginsRuntimeOnly(projects.xdclPluginDevelopmentPlugin)

    // The embedded Maven repository (repo/ in the image): the published ecosystem libraries at the
    // distribution version, plus the org.xdcl API module their published metadata strictly
    // requires — the full offline-resolution closure a consumer build's settings classpath needs
    // when the XDCL provider injects built-in ecosystems into dependency resolution.
    distributionRepositoryOnly(projects.xdclCommonEcosystem)
    distributionRepositoryOnly(projects.xdclJvmEcosystem)
    distributionRepositoryOnly(projects.xdclJvmCheckstyle)
    distributionRepositoryOnly(projects.xdclJvmInstrumentation)
    distributionRepositoryOnly(projects.xdclGroovyEcosystem)
    distributionRepositoryOnly(projects.xdclPluginDevelopment)
    distributionRepositoryOnly(libs.xdclGradleApi)
}

// External plugin modules don't follow the gradle-<name>.jar naming the manifest derives module
// names from; register them explicitly so the distribution's plugins classloader picks them up
// (which is what lets `plugins { id "…" }` resolve like a builtin).
tasks.named<gradlebuild.packaging.tasks.PluginsManifest>("implementationPluginsManifest") {
    additionalPlugins.add("xdcl-gradle-plugin")
    additionalPlugins.add("java-ecosystem")
    additionalPlugins.add("checkstyle-ecosystem")
    additionalPlugins.add("instrumentation-ecosystem")
    additionalPlugins.add("groovy-ecosystem")
    additionalPlugins.add("plugin-development-ecosystem")
}

// This is required for the separate promotion build and should be adjusted there in the future
val buildEnvironmentExtension = extensions.getByType(BuildEnvironmentExtension::class)
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(buildEnvironmentExtension.rootProjectBuildDir.dir("distributions"))
}
