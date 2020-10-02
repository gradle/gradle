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

import gradlebuild.integrationtests.TestType
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.addSourceSet
import gradlebuild.integrationtests.configureIde
import gradlebuild.integrationtests.createTasks
import gradlebuild.integrationtests.createTestTask

plugins {
    java
    id("gradlebuild.module-identity")
    id("gradlebuild.dependency-modules")
}

val sourceSet = addSourceSet(TestType.CROSSVERSION)
addDependenciesAndConfigurations(TestType.CROSSVERSION.prefix)
createTasks(sourceSet, TestType.CROSSVERSION)
createAggregateTasks(sourceSet)
configureIde(TestType.CROSSVERSION)
configureTestFixturesForCrossVersionTests()

fun configureTestFixturesForCrossVersionTests() {
    if (name != "test") {
        dependencies {
            "crossVersionTestImplementation"(testFixtures(project(":tooling-api")))
        }
    }
}

fun createAggregateTasks(sourceSet: SourceSet) {
    // Calculate the set of released versions - do this at configuration time because we need this to create various tasks
    val allVersionsCrossVersionTests = tasks.register("allVersionsCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
    }

    val quickFeedbackCrossVersionTests = tasks.register("quickFeedbackCrossVersionTests") {
        group = "verification"
        description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
    }

    val releasedVersions = moduleIdentity.releasedVersions.forUseAtConfigurationTime().getOrNull()
    releasedVersions?.allTestedVersions?.forEach { targetVersion ->
        val crossVersionTest = createTestTask(
            "gradle${targetVersion.version}CrossVersionTest", "forking", sourceSet, TestType.CROSSVERSION,
            Action {
                this.description = "Runs the cross-version tests against Gradle ${targetVersion.version}"
                this.systemProperties["org.gradle.integtest.versions"] = targetVersion.version
            }
        )

        allVersionsCrossVersionTests.configure { dependsOn(crossVersionTest) }
        if (targetVersion in releasedVersions.mainTestedVersions) {
            quickFeedbackCrossVersionTests.configure { dependsOn(crossVersionTest) }
        }
    }
}
