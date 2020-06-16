import org.gradle.plugins.install.Install
import org.gradle.gradlebuild.packaging.RunEmbeddedGradle
import org.gradle.gradlebuild.packaging.GradleDistributionSpecs.binDistributionSpec
import org.gradle.gradlebuild.packaging.GradleDistributionSpecs.allDistributionSpec

plugins {
    gradlebuild.distribution.packaging
    gradlebuild.`add-verify-production-environment-task`
    gradlebuild.install
}

dependencies {
    coreRuntimeOnly(platform(project(":corePlatform")))

    pluginsRuntimeOnly(platform(project(":distributionsPublishing")))
    pluginsRuntimeOnly(platform(project(":distributionsJvm")))
    pluginsRuntimeOnly(platform(project(":distributionsNative")))

    pluginsRuntimeOnly(project(":buildInit"))
    pluginsRuntimeOnly(project(":buildProfile"))
    pluginsRuntimeOnly(project(":antlr"))
    pluginsRuntimeOnly(project(":enterprise"))

    // The following are scheduled to be removed from the distribution completely in Gradle 7.0
    pluginsRuntimeOnly(project(":javascript"))
    pluginsRuntimeOnly(project(":platformPlay"))
    pluginsRuntimeOnly(project(":idePlay"))
}

tasks.register<Install>("install") {
    description = "Installs the minimal distribution"
    group = "build"
    with(binDistributionSpec())
}

tasks.register<Install>("installAll") {
    description = "Installs the full distribution"
    group = "build"
    with(allDistributionSpec())
}

tasks.register<RunEmbeddedGradle>("runDevGradle") {
    group = "verification"
    description = "Runs an embedded Gradle using the partial distribution for ${project.path}."
    gradleClasspath.from(configurations.runtimeClasspath.get(), tasks.runtimeApiInfoJar)
}

// This is required for the separate promotion build and should be adjusted there in the future
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(rootProject.layout.buildDirectory.dir("distributions"))
}
