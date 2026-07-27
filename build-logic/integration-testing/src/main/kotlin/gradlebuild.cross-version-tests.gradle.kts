/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import gradlebuild.basics.capitalize
import gradlebuild.basics.testing.TestType
import gradlebuild.identity.extension.ReleasedVersionsDetails
import gradlebuild.integrationtests.CROSSVERSION_TEST_MODELS
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.configureTestSourceSetInIde
import gradlebuild.integrationtests.createTestTask
import gradlebuild.integrationtests.crossVersionTestModels
import gradlebuild.integrationtests.setSystemPropertiesOfTestJVM
import gradlebuild.integrationtests.tasks.IntegrationTest

plugins {
    java
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.jvm-compile")
}

val crossVersionTestModelsSourceSet = sourceSets.create(CROSSVERSION_TEST_MODELS)
val crossVersionTestSourceSet = sourceSets.create("${TestType.CROSSVERSION.prefix}Test")
val releasedVersions = gradleModule.identity.releasedVersions.orNull

jvmCompile {
    addCompilationFrom(crossVersionTestSourceSet)
    // crossVersion tests must be able to run the TAPI client, which is still JVM 8 compatible,
    // code may also run in Gradle versions that may not support the JVM version used to compile
    // the production code for the in-development Gradle version
    addCompilationFrom(crossVersionTestModelsSourceSet) {
        targetJvmVersion = 8
    }
}

addDependenciesAndConfigurations(TestType.CROSSVERSION.prefix)
createQuickFeedbackTasks(crossVersionTestSourceSet, releasedVersions)
createAggregateTasks(crossVersionTestSourceSet, releasedVersions)
configureTestSourceSetInIde(crossVersionTestModelsSourceSet)
configureTestSourceSetInIde(crossVersionTestSourceSet)
configureCrossVersionTestModelsVariant(crossVersionTestModelsSourceSet)
configureDependenciesForCrossVersionTests(crossVersionTestSourceSet, crossVersionTestModelsSourceSet)

fun configureCrossVersionTestModelsVariant(modelsSourceSet: SourceSet) {
    val crossVersionTestModelsApi = configurations.maybeCreate(modelsSourceSet.apiConfigurationName)

    val crossVersionTestModelsJar = tasks.register(modelsSourceSet.jarTaskName, Jar::class.java) {
        description = "Assembles a JAR archive containing the classes of the '${modelsSourceSet.name}' outputs."
        group = BasePlugin.BUILD_GROUP
        from(modelsSourceSet.output)
        archiveClassifier = modelsSourceSet.name
    }

    configurations.create("${modelsSourceSet.name}Elements") {
        isCanBeConsumed = true
        isCanBeResolved = false
        extendsFrom(crossVersionTestModelsApi)
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        }
        // Declare a capability so consumers can request models specifically
        outgoing.capability("${project.group}:${project.name}-${modelsSourceSet.name}:${project.version}")
        outgoing.artifact(crossVersionTestModelsJar)
    }
}

fun configureDependenciesForCrossVersionTests(sourceSet: SourceSet, modelsSourceSet: SourceSet) {
    dependencies {
        // make sure dependency versions are pinned to current desired versions
        project.configurations[modelsSourceSet.compileOnlyConfigurationName](platform(project(":distributions-dependencies")))
        // compile cross version test models using the current TAPI in the form available to our users later
        project.configurations[modelsSourceSet.compileOnlyConfigurationName](project(":tooling-api")) {
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
            }
        }

        project.configurations[sourceSet.implementationConfigurationName](crossVersionTestModels(project(path)))
        project.configurations[sourceSet.implementationConfigurationName](testFixtures(project(":tooling-api")))
    }
}

fun createQuickFeedbackTasks(sourceSet: SourceSet, releasedVersions: ReleasedVersionsDetails?) {
    val testType = TestType.CROSSVERSION
    val defaultExecuter = "embedded"
    val prefix = testType.prefix
    testType.executers.forEach { executer ->
        val taskName = "$executer${prefix.capitalize()}Test"
        val testTask = createTestTask(taskName, executer, sourceSet, testType) {
            this.setSystemPropertiesOfTestJVM("latest")
            this.systemProperties["org.gradle.integtest.crossVersion"] = "true"
            this.systemProperties["org.gradle.integtest.crossVersion.lowestTestedVersion"] = releasedVersions?.lowestTestedVersion?.version

            // We should always be using JUnitPlatform at this point, so don't call useJUnitPlatform(), else this will
            // discard existing options configuration and add a deprecation warning.  Just set the existing options.
            (this.testFramework.options as JUnitPlatformOptions).includeEngines("cross-version-test-engine")
            addClasspathSystemPropertyArgumentProvider()
        }
        if (executer == defaultExecuter) {
            // The test task with the default executer runs with 'check'
            tasks.named("check").configure { dependsOn(testTask) }
        }
    }

    tasks.named("quickTest") {
        dependsOn("embeddedCrossVersionTest")
    }

    tasks.named("platformTest") {
        dependsOn("forkingCrossVersionTest")
    }

    tasks.named("quickFeedbackCrossVersionTest") {
        dependsOn("quickFeedbackCrossVersionTests")
    }
}

fun IntegrationTest.addClasspathSystemPropertyArgumentProvider() {
    // using `integTest` is a bad approximation that works well enough with the current TAPI cross version test ClassLoader setup
    // A configuration with just the cross version test infrastructure should be used here instead
    // see https://github.com/gradle/gradle/issues/37033 for more details
    val integTest = project.sourceSets.named("integTest").map { it.runtimeClasspath }
    val projectPath = project.projectDir.absoluteFile.toPath()

    // set the classpath required to load & execute tests as a system property as well so an isolating ClassLoader can use it to execute test with an old TAPI version
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        val diff = (classpath - integTest.get()).map { projectPath.relativize(it.absoluteFile.toPath()) }
        listOf("-Dorg.gradle.integtest.crossVersion.testClasspath=" + diff.joinToString(":"))
    })
}

fun createAggregateTasks(sourceSet: SourceSet, releasedVersions: ReleasedVersionsDetails?) {
    tasks.register("allVersionsCrossVersionTest") {
        description = "Run cross-version tests against all released versions (latest patch release of each)"
        group = "ci lifecycle"
    }

    tasks.named("allVersionsCrossVersionTest") {
        dependsOn("allVersionsCrossVersionTests")
    }

    val allVersionsCrossVersionTests = tasks.register("allVersionsCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
    }

    val quickFeedbackCrossVersionTests = tasks.register("quickFeedbackCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
    }

    releasedVersions?.allTestedVersions?.forEach { targetVersion ->
        val crossVersionTest = createTestTask("gradle${targetVersion.version}CrossVersionTest", "forking", sourceSet, TestType.CROSSVERSION) {
            this.description = "Runs the cross-version tests against Gradle ${targetVersion.version}"
            this.systemProperties["org.gradle.integtest.versions"] = targetVersion.version
            this.systemProperties["org.gradle.integtest.crossVersion"] = "true"
            this.systemProperties["org.gradle.integtest.crossVersion.lowestTestedVersion"] = releasedVersions.lowestTestedVersion.version
            this.useJUnitPlatform {
                includeEngines("cross-version-test-engine")
            }
            addClasspathSystemPropertyArgumentProvider()
        }

        allVersionsCrossVersionTests.configure { dependsOn(crossVersionTest) }
        if (targetVersion in releasedVersions.mainTestedVersions) {
            quickFeedbackCrossVersionTests.configure { dependsOn(crossVersionTest) }
        }
    }
}
