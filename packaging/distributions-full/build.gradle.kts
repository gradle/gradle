import gradlebuild.basics.BuildEnvironmentExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.verify-build-environment")
    id("gradlebuild.install")
}

description = "The collector project for the entirety of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

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
}

// This is required for the separate promotion build and should be adjusted there in the future
val buildEnvironmentExtension = extensions.getByType(BuildEnvironmentExtension::class)
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(buildEnvironmentExtension.rootProjectBuildDir.dir("distributions"))
}

data class ExternalModule(val group: String, val name: String, val version: String) {
    val ga: String
        get() = "$group:$name"
}

val generatedLicenseStartMarker = "=== BEGIN GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="
val generatedLicenseEndMarker = "=== END GENERATED DISTRIBUTION DEPENDENCY LICENSES ==="

fun resolveDistributionExternalModules(): Set<ExternalModule> {
    val runtimeClasspath = configurations.getByName("runtimeClasspath")
    return runtimeClasspath.incoming.resolutionResult.allComponents
        .mapNotNull { component -> component.id as? ModuleComponentIdentifier }
        .map { id -> ExternalModule(id.group, id.module, id.version) }
        .toSet()
}

fun renderGeneratedLicenseSection(modules: Set<ExternalModule>): String {
    val body = buildString {
        appendLine(generatedLicenseStartMarker)
        appendLine("------------------------------------------------------------------------------")
        appendLine("Generated distribution dependency listing.")
        appendLine("Run :distributions-full:updateLicenseDependencies to regenerate.")
        appendLine("------------------------------------------------------------------------------")
        appendLine()
        modules.map { it.ga }.distinct().sorted().forEach { ga -> appendLine(ga) }
        appendLine()
        appendLine(generatedLicenseEndMarker)
    }
    return body.trimEnd()
}

val checkExternalDependenciesAreListedInLicense by tasks.registering {
    description = "Verifies that all external distribution dependencies are listed in LICENSE."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    notCompatibleWithConfigurationCache("Resolves runtime classpath during task execution.")

    doLast {
        val externalModules = resolveDistributionExternalModules().map { it.ga }.toSet()
        val licenseContent = rootDir.resolve("LICENSE").readText()
        val missingModules = externalModules.filterNot(licenseContent::contains).sorted()
        if (missingModules.isNotEmpty()) {
            error(
                """
                The following external dependencies are missing from LICENSE:
                ${missingModules.joinToString(separator = "\n")}

                Run :distributions-full:updateLicenseDependencies to update LICENSE automatically.
                """.trimIndent()
            )
        }
    }
}

val updateLicenseDependencies by tasks.registering {
    description = "Updates LICENSE with an auto-generated section for external distribution dependencies."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    notCompatibleWithConfigurationCache("Resolves runtime classpath during task execution.")

    doLast {
        val licenseFile = rootDir.resolve("LICENSE")
        val currentContent = licenseFile.readText()
        val generatedSection = renderGeneratedLicenseSection(resolveDistributionExternalModules())

        val startIndex = currentContent.indexOf(generatedLicenseStartMarker)
        val endIndex = currentContent.indexOf(generatedLicenseEndMarker)
        val updatedContent = if (startIndex >= 0 && endIndex >= 0 && endIndex > startIndex) {
            val endExclusive = endIndex + generatedLicenseEndMarker.length
            currentContent.substring(0, startIndex) + generatedSection + currentContent.substring(endExclusive)
        } else {
            currentContent.trimEnd() + "\n\n" + generatedSection + "\n"
        }
        licenseFile.writeText(updatedContent)
    }
}

tasks.named("check") {
    dependsOn(checkExternalDependenciesAreListedInLicense)
}
