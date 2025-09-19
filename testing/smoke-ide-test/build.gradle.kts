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
val ideStarterBuildDir = layout.buildDirectory.dir("ideStarter")

plugins.withType<IdeaPlugin> {
    with(model) {
        module {
            testSources.from(smokeIdeTestSourceSet.java.srcDirs, smokeIdeTestSourceSet.groovy.srcDirs)
            testResources.from(smokeIdeTestSourceSet.resources.srcDirs)
        }
    }
}

abstract class IdeStarterPathProvider : CommandLineArgumentProvider {
    @get: InputDirectory
    @get: PathSensitive(PathSensitivity.RELATIVE)
    abstract val ideStarterDir : DirectoryProperty

    override fun asArguments(): Iterable<String> =
        listOf("-Dide.starter.path=${ideStarterDir.get().asFile.absolutePath}")
}

tasks {
    val unzipIdeStarter by registering(Sync::class) {
        from(zipTree(ideStarter.elements.map { it.single() }))
        into(ideStarterBuildDir)
    }

    val fetchGradle by registering(RemoteProject::class) {
        remoteUri = rootDir.absolutePath
        ref = buildCommitId
    }

    val shrinkGradle by registering(Sync::class) {
        from(fetchGradle.map { it.outputDirectory }) {
            exclude("subprojects/*/*/src/**")
            exclude("testing/*/*/src/**")
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
        jvmArgumentProviders.add(
            objects.newInstance<IdeStarterPathProvider>().apply {
                ideStarterDir = ideStarterBuildDir
            }
        )
    }
}

dependencies {
    ideStarter(libs.gradleIdeStarter)
    smokeIdeTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
    smokeIdeTestImplementation(libs.gradleIdeStarterScenarios)
    smokeIdeTestImplementation(testFixtures(projects.core))
}

integTest.testJvmXmx = "5g"

errorprone {
    nullawayEnabled = true
}
