/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.gradlebuild.test.integrationtests

import com.google.common.collect.MultimapBuilder
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.*
import releasedVersions


class CrossVersionTestsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSet = addSourceSet(TestType.CROSSVERSION)
        addDependenciesAndConfigurations(TestType.CROSSVERSION)
        dependencies {
            "crossVersionTestRuntimeOnly"(project(":toolingApiBuilders"))
        }
        createTasks(sourceSet, TestType.CROSSVERSION)
        createAggregateTasks(sourceSet)
        configureIde(TestType.CROSSVERSION)
        configureTestFixturesForCrossVersionTests()
    }

    private
    fun Project.configureTestFixturesForCrossVersionTests() {
        dependencies {
            "testImplementation"(testFixtures(project(":toolingApi")))
        }
    }

    private
    fun Project.createAggregateTasks(sourceSet: SourceSet) {
        // Calculate the set of released versions - do this at configuration time because we need this to create various tasks
        val allVersionsCrossVersionTests = tasks.register("allVersionsCrossVersionTests") {
            group = "verification"
            description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
        }

        val quickFeedbackCrossVersionTests = tasks.register("quickFeedbackCrossVersionTests") {
            group = "verification"
            description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
        }

        val quickTestVersions = releasedVersions.getTestedVersions(true)
        val allTestVersions = releasedVersions.getTestedVersions(false)
        val testVersionsEnabledInCurrentSplit = getTestVersionsEnabledInCurrentSplit(allTestVersions)
        if (testVersionsEnabledInCurrentSplit.size != allTestVersions.size) {
            println("Only enable ${testVersionsEnabledInCurrentSplit.joinToString(", ")} for $name cross version tests")
        }
        allTestVersions.forEach { targetVersion ->
            val crossVersionTest = createTestTask("gradle${targetVersion}CrossVersionTest", "forking", sourceSet, TestType.CROSSVERSION, Action {
                this.description = "Runs the cross-version tests against Gradle $targetVersion"
                this.systemProperties["org.gradle.integtest.versions"] = targetVersion
                enabled = targetVersion in testVersionsEnabledInCurrentSplit
            })

            allVersionsCrossVersionTests.configure { dependsOn(crossVersionTest) }
            if (targetVersion in quickTestVersions) {
                quickFeedbackCrossVersionTests.configure { dependsOn(crossVersionTest) }
            }
        }
    }

    // Sample the list, for example, allTestVersions is [1.0, 1.1, 1.2, 1.3, 1.4]
    // -PtestSplit=1/2 return [1.0, 1,2, 1.4]
    // -PtestSplit=2/2 return [1.1, 1,3]
    private
    fun Project.getTestVersionsEnabledInCurrentSplit(allTestVersions: List<String>): List<String> {
        val testSplit = project.stringPropertyOrEmpty("testSplit")
        if (testSplit.isBlank()) {
            return allTestVersions
        }
        val currentSplit = testSplit.split("/")[0].toInt()
        val numberOfSplits = testSplit.split("/")[1].toInt()
        val buckets = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build<Int, String>()
        for ((index, version) in allTestVersions.withIndex()) {
            buckets.put(index % numberOfSplits, version)
        }
        return buckets[currentSplit - 1]
    }
}
