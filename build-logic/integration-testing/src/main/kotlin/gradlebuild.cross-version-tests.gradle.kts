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
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.addSourceSet
import gradlebuild.integrationtests.configureIde
import gradlebuild.integrationtests.createTestTask
import gradlebuild.integrationtests.setSystemPropertiesOfTestJVM

plugins {
    java
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
}

val sourceSet = addSourceSet(TestType.CROSSVERSION)
addDependenciesAndConfigurations(TestType.CROSSVERSION.prefix)
createQuickFeedbackTasks()
createAggregateTasks(sourceSet)
configureIde(TestType.CROSSVERSION)
configureTestFixturesForCrossVersionTests()

fun configureTestFixturesForCrossVersionTests() {
    // do not attempt to find projects when the plugin is applied just to generate accessors
    if (project.name != "gradle-kotlin-dsl-accessors" && project.name != "test" /* remove once wrapper is updated */) {
        dependencies {
            "crossVersionTestImplementation"(testFixtures(project(":tooling-api")))
        }
    }
}
val releasedVersions = moduleIdentity.releasedVersions.orNull

fun createQuickFeedbackTasks() {
    val testType = TestType.CROSSVERSION
    val defaultExecuter = "embedded"
    val prefix = testType.prefix
    testType.executers.forEach { executer ->
        val taskName = "$executer${prefix.capitalize()}Test"
        val testTask = createTestTask(taskName, executer, sourceSet, testType) {
            this.setSystemPropertiesOfTestJVM("latest")
            systemProperty("org.gradle.integtest.crossVersion", "true")
            releasedVersions?.lowestTestedVersion?.version?.let {
                systemProperty("org.gradle.integtest.crossVersion.lowestTestedVersion", it)
            }

            // We should always be using JUnitPlatform at this point, so don't call useJUnitPlatform(), else this will
            // discard existing options configuration and add a deprecation warning.  Just set the existing options.
            (this.options as JUnitPlatformOptions).includeEngines("cross-version-test-engine")
        }
        if (executer == defaultExecuter) {
            // The test task with the default executer runs with 'check'
            tasks.named("check").configure { dependsOn(testTask) }
        }
    }
}

fun createAggregateTasks(sourceSet: SourceSet) {
    val allVersionsCrossVersionTests = tasks.register("allVersionsCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
    }

    val quickFeedbackCrossVersionTests = tasks.register("quickFeedbackCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
    }

    val releasedVersions = moduleIdentity.releasedVersions.orNull
    releasedVersions?.allTestedVersions?.forEach { targetVersion ->
        val crossVersionTest = createTestTask("gradle${targetVersion.version}CrossVersionTest", "forking", sourceSet, TestType.CROSSVERSION) {
            this.description = "Runs the cross-version tests against Gradle ${targetVersion.version}"
            systemProperty("org.gradle.integtest.versions", targetVersion.version)
            systemProperty("org.gradle.integtest.crossVersion", "true")
            systemProperty("org.gradle.integtest.crossVersion.lowestTestedVersion", releasedVersions.lowestTestedVersion.version)
            this.useJUnitPlatform {
                includeEngines("cross-version-test-engine")
            }
        }

        allVersionsCrossVersionTests.configure { dependsOn(crossVersionTest) }
        if (targetVersion in releasedVersions.mainTestedVersions) {
            quickFeedbackCrossVersionTests.configure { dependsOn(crossVersionTest) }
        }
    }
}
