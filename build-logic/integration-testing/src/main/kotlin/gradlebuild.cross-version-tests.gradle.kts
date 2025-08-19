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

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import gradlebuild.basics.capitalize
import gradlebuild.basics.testing.TestType
import gradlebuild.identity.extension.ReleasedVersionsDetails
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.createTestTask
import gradlebuild.integrationtests.configureIde
import gradlebuild.integrationtests.setSystemPropertiesOfTestJVM
import java.nio.charset.StandardCharsets

plugins {
    java
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.jvm-compile")
}

project.layout.settingsDirectory.file(".teamcity/subprojects.json").asFile.apply {
    if (exists()) {
        val json = GsonBuilder().create().fromJson(readText(StandardCharsets.UTF_8), JsonArray::class.java)
        val subprojectInfo = json.firstOrNull { jsonElement ->
            jsonElement.asJsonObject.get("name").asString.equals(project.name)
        }

        if (subprojectInfo?.asJsonObject?.get("crossVersionTests")?.asBoolean == true) {
            val sourceSet = sourceSets.create("${TestType.CROSSVERSION.prefix}Test")
            val releasedVersions = gradleModule.identity.releasedVersions.orNull

            jvmCompile {
                // crossVersion tests must be able to run the TAPI client, which is still JVM 8 compatible,
                // code may also run in Gradle versions that may not support the JVM version used to compile
                // the production code for the in-development Gradle version
                addCompilationFrom(sourceSet) {
                    targetJvmVersion = 8
                }
            }

            addDependenciesAndConfigurations(TestType.CROSSVERSION.prefix)
            createQuickFeedbackTasks(sourceSet, releasedVersions)
            createAggregateTasks(sourceSet, releasedVersions)
            configureIde(TestType.CROSSVERSION)
            configureDependenciesForCrossVersionTests()
        }
    }
}

fun configureDependenciesForCrossVersionTests() {
    // do not attempt to find projects when the plugin is applied just to generate accessors
    if (project.name != "gradle-kotlin-dsl-accessors" && project.name != "test" /* remove once wrapper is updated */) {
        dependencies {
            "crossVersionTestImplementation"(testFixtures(project(":tooling-api")))
        }
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
        }
        if (executer == defaultExecuter) {
            // The test task with the default executer runs with 'check'
            tasks.named("check").configure { dependsOn(testTask) }
        }
    }
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
        }

        allVersionsCrossVersionTests.configure { dependsOn(crossVersionTest) }
        if (targetVersion in releasedVersions.mainTestedVersions) {
            quickFeedbackCrossVersionTests.configure { dependsOn(crossVersionTest) }
        }
    }
}
