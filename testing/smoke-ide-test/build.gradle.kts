import gradlebuild.basics.BuildEnvironment
import gradlebuild.basics.buildCommitId
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.configureTestSourceSetInIde
import gradlebuild.integrationtests.ide.androidStudioSystemProperties
import gradlebuild.integrationtests.ide.ideaSystemProperties
import gradlebuild.integrationtests.tasks.SmokeIdeTest
import gradlebuild.performance.generator.tasks.RemoteProject

plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.ide-provisioning")
}

description = "Tests are checking Gradle behavior during IDE synchronization process"

val smokeIdeTestSourceSet = sourceSets.create("smokeIdeTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configureTestSourceSetInIde(smokeIdeTestSourceSet)

jvmCompile {
    addCompilationFrom(smokeIdeTestSourceSet)
}

dependencyAnalysis {
    issues {
        ignoreSourceSet(smokeIdeTestSourceSet.name)
    }
}

addDependenciesAndConfigurations("smokeIde")

val smokeIdeTestImplementation = configurations.getByName("smokeIdeTestImplementation")
val smokeIdeTestDistributionRuntimeOnly = configurations.getByName("smokeIdeTestDistributionRuntimeOnly")

tasks {
    val fetchGradle = register<RemoteProject>("fetchGradle") {
        remoteUri = rootDir.absolutePath
        ref = buildCommitId
    }

    val shrinkGradle = register<Sync>("shrinkGradle") {
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
        dependsOn(shrinkGradle)
        group = "Verification"
        maxParallelForks = 1
        systemProperties["org.gradle.integtest.executer"] = "forking"
        // IDEA resolves gradleJvm=#JAVA_HOME via the IDE process's JAVA_HOME env var.
        // The TC agent sets JAVA_HOME on the Gradle build, but Test forks don't always
        // propagate it. Set it explicitly so the spawned IDE inherits a valid value.
        environment("JAVA_HOME", javaLauncher.get().metadata.installationPath.asFile.absolutePath)
        testClassesDirs = smokeIdeTestSourceSet.output.classesDirs
        classpath = smokeIdeTestSourceSet.runtimeClasspath
        jvmArgumentProviders.add(ideaSystemProperties())
        jvmArgumentProviders.add(androidStudioSystemProperties())
        // IDEA's launch script requires JAVA_HOME. CI agents don't set it in the agent
        // environment, and gradle-profiler's IdeLauncher only forwards IDEA_VM_OPTIONS /
        // IDEA_PROPERTIES — so the IDE child process inherits an empty JAVA_HOME and fails.
        // Test.environment() stores values as-is and calls toString() at fork time, so the
        // toolchain provider must be resolved eagerly.
        environment("JAVA_HOME", project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }.get().metadata.installationPath.asFile.absolutePath)
    }
}

dependencies {
    smokeIdeTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("Tests starts an IDE with using current Gradle distribution")
    }
    smokeIdeTestImplementation(projects.internalIntegTesting)
    smokeIdeTestImplementation(testLibs.gradleProfiler)
    smokeIdeTestImplementation(testFixtures(projects.core))
}

integTest.testJvmXmx = "5g"

errorprone {
    nullawayEnabled = true
}
