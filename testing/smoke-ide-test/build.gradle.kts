import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.buildCommitId
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.tasks.SmokeIdeTest
import gradlebuild.performance.generator.tasks.RemoteProject

plugins {
    id("gradlebuild.internal.java")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

jvmCompile {
    addCompilationFrom(smokeIdeTestSourceSet)
}

dependencyAnalysis {
    issues {
        ignoreSourceSet(smokeIdeTestSourceSet.name)
    }
}

addDependenciesAndConfigurations("smokeIde")

val smokeIdeTestImplementation: Configuration by configurations
val smokeIdeTestDistributionRuntimeOnly: Configuration by configurations
val ideStarter by configurations.creating {
    isCanBeConsumed = false
}

plugins.withType<IdeaPlugin> {
    with(model) {
        module {
            testSources.from(smokeIdeTestSourceSet.java.srcDirs, smokeIdeTestSourceSet.groovy.srcDirs)
            testResources.from(smokeIdeTestSourceSet.resources.srcDirs)
        }
    }
}

tasks {
    val unzipIdeStarter by registering(Sync::class) {
        from(zipTree(ideStarter.elements.map { it.single() }))
        into(layout.buildDirectory.dir("ideStarter"))
    }

    val fetchGradle by registering(RemoteProject::class) {
        remoteUri = rootDir.absolutePath
        ref = buildCommitId
    }

    val shrinkGradle by registering(Sync::class) {
        from(fetchGradle.map { it.outputDirectory }) {
            exclude("subprojects/*/*/src/**")
            filesMatching("platforms/*/*/src/**") {
                // /platforms/documentation/docs/samples must be included
                if (!sourcePath.contains("documentation/docs/samples/templates")) {
                    exclude()
                }
            }
            exclude(".idea/**")
            exclude(".github/**")
            exclude(".teamcity/**")
        }
        into(layout.buildDirectory.dir("gradleSources"))
    }

    if (BuildEnvironment.isCiServer) {
        fetchGradle {
            doNotTrackState("Do a full checkout on CI")
        }
    }

    register<SmokeIdeTest>("smokeIdeTest") {
        dependsOn(unzipIdeStarter, shrinkGradle)
        group = "Verification"
        maxParallelForks = 1
        systemProperties["org.gradle.integtest.executer"] = "forking"
        testClassesDirs = smokeIdeTestSourceSet.output.classesDirs
        classpath = smokeIdeTestSourceSet.runtimeClasspath
    }
}

dependencies {
    ideStarter(libs.gradleIdeStarter)
    smokeIdeTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
    smokeIdeTestImplementation(testFixtures(projects.core))
}

integTest.testJvmXmx = "1g"
