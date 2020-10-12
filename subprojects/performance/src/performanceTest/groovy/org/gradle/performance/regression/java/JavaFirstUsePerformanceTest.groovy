/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearGradleUserHomeMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator
import org.junit.experimental.categories.Category

import static org.gradle.performance.generator.JavaTestProjectGenerator.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL

@Category(SlowPerformanceRegressionTest)
class JavaFirstUsePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.targetVersions = ["6.8-20201011220037+0000"]
    }

    def "first use"() {
        given:
        runner.gradleOpts = runner.projectMemoryOptions
        runner.tasksToRun = ['tasks']
        runner.runs = (runner.testProject == LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL.projectName) ? 5 : 10
        runner.useDaemon = false
        runner.addBuildMutator { invocationSettings ->
            new ClearGradleUserHomeMutator(invocationSettings.gradleUserHome, AbstractCleanupMutator.CleanupSchedule.BUILD)
        }
        runner.addBuildMutator { invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean checkout"() {
        given:
        runner.gradleOpts = runner.projectMemoryOptions
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.addBuildMutator { invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "cold daemon"() {
        given:
        runner.gradleOpts = runner.projectMemoryOptions
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
