import gradlebuild.integrationtests.tasks.SmokeIdeTest
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.ide.AndroidStudioProvisioningExtension

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.android-studio-provisioning")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

addDependenciesAndConfigurations("smokeIde")
val smokeIdeTestImplementation: Configuration by configurations
val smokeIdeTestDistributionRuntimeOnly: Configuration by configurations

plugins.withType<IdeaPlugin> {
    with(model) {
        module {
            testSources.from(smokeIdeTestSourceSet.java.srcDirs, smokeIdeTestSourceSet.groovy.srcDirs)
            testResources.from(smokeIdeTestSourceSet.resources.srcDirs)
        }
    }
}

tasks.register<SmokeIdeTest>("smokeIdeTest") {
    group = "Verification"
    maxParallelForks = 1
    systemProperties["org.gradle.integtest.executer"] = "forking"
    testClassesDirs = smokeIdeTestSourceSet.output.classesDirs
    classpath = smokeIdeTestSourceSet.runtimeClasspath

    val jvmArgumentProvider = project.extensions.getByType<AndroidStudioProvisioningExtension>().androidStudioSystemProperties(project, emptyList())
    jvmArgumentProviders.add(jvmArgumentProvider)
}

dependencies {
    smokeIdeTestImplementation(libs.gradleProfiler)
    smokeIdeTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
}
