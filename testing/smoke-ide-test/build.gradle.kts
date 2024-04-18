import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeIdeTest

plugins {
    id("gradlebuild.internal.java")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

addDependenciesAndConfigurations("smokeIde")

val smokeIdeTestImplementation: Configuration by configurations
val smokeIdeTestDistributionRuntimeOnly: Configuration by configurations
val ideStarter by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
}

val unzipIdeStarter by tasks.registering(ProcessResources::class) {
    from(zipTree(ideStarter.elements.map { it.single() }))
    into(layout.buildDirectory.dir("ideStarter"))
}

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
    dependsOn(unzipIdeStarter)
}

dependencies {
    ideStarter(libs.gradleIdeStarter)
    smokeIdeTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
}
