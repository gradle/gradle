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

package org.gradle.testkit.scenario

import org.gradle.testkit.runner.fixtures.CustomEnvironmentVariables
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.scenario.collection.IncrementalBuildScenario
import org.gradle.testkit.scenario.collection.InstantExecutionScenarios


class InstantExecutionScenariosIntegrationTest extends AbstractGradleScenarioIntegrationTest {

    def "can enable and assert on instant execution on an ad-hoc scenario"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withRunnerAction(InstantExecutionScenarios.getEnableInstantExecution())
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->

                steps.named("first")
                    .withResult { result ->
                        InstantExecutionScenarios.getAssertStepStored().accept("first", result)
                    }

                steps.named("second")
                    .withResult { result ->
                        InstantExecutionScenarios.getAssertStepLoaded().accept("second", result)
                    }
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep("first")
        result.ofStep("second")
    }

    def "can run instant execution cache invalidation scenario for system property"() {

        given:
        def scenario = InstantExecutionScenarios.createCacheInvalidation()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withSystemPropertyBuildLogicInput(
                underTestBuildLogicInputSystemPropertyName,
                "value1",
                "value2"
            )

        when:
        def result = scenario.run()

        then:
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.STORE)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.LOAD)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.INVALIDATE)
    }

    @NoDebug
    @CustomEnvironmentVariables
    def "can run instant execution cache invalidation scenario for environment variable"() {

        given:
        def scenario = InstantExecutionScenarios.createCacheInvalidation()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withEnvironmentVariableBuildLogicInput(
                underTestBuildLogicInputEnvironmentVariableName,
                "value1",
                "value2"
            )

        when:
        def result = scenario.run()

        then:
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.STORE)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.LOAD)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.INVALIDATE)
    }

    def "can run instant execution cache invalidation scenario for file content"() {

        given:
        def scenario = InstantExecutionScenarios.createCacheInvalidation()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withFileContentBuildLogicInput(
                underTestBuildLogicInputFileName,
                "value1",
                "value2"
            )

        when:
        def result = scenario.run()

        then:
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.STORE)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.LOAD)
        result.ofStep(InstantExecutionScenarios.CacheInvalidation.Steps.INVALIDATE)
    }

    def "cache invalidation scenario fails when build logic input does not change"() {

        given:
        def scenario = InstantExecutionScenarios.createCacheInvalidation()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withSystemPropertyBuildLogicInput(
                underTestBuildLogicInputSystemPropertyName,
                "SAME",
                "SAME"
            )

        when:
        scenario.run()

        then:
        def ex = thrown(AssertionError)
        ex.message == "Step 'invalidate': expected storing instant execution state"
    }

    def "can run incremental build scenario with instant execution enabled"() {

        given:
        def scenario = InstantExecutionScenarios.createIncrementalBuild()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withInputChangeWorkspaceAction { root ->
                new File(root, underTestTaskInputFilePath) << 'CHANGED'
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep(IncrementalBuildScenario.Steps.CLEAN_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.UP_TO_DATE_BUILD)
        result.ofStep(IncrementalBuildScenario.Steps.INCREMENTAL_BUILD)
    }

    @NoDebug // fails with classloading issue "org.apache.groovy.json.FastStringServiceFactory: org.apache.groovy.json.DefaultFastStringServiceFactory not a subtype"
    def "incremental build scenario fails if instant execution problems are found"() {

        given:
        def scenario = InstantExecutionScenarios.createIncrementalBuild()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                underTestWorkspace.execute(root)
                new File(root, 'build.gradle') << """
                    tasks.named("$underTestTaskName") {
                        doLast {
                            println(project)
                        }
                    }
                """
            }
            .withTaskPaths(underTestTaskPath)
            .withInputChangeWorkspaceAction { root ->
                new File(root, underTestTaskInputFilePath) << 'CHANGED'
            }

        when:
        scenario.run()

        then:
        def ex = thrown(UnexpectedScenarioStepFailure)
        ex.step == IncrementalBuildScenario.Steps.CLEAN_BUILD
        ex.buildResult.output.contains("1 instant execution problem was found.")
        ex.buildResult.output.contains("task `:underTest` of type `UnderTestTask`: invocation of 'Task.project' at execution time is unsupported.")
    }

    def "build cache scenario is unsupported with instant execution"() {

        when:
        InstantExecutionScenarios.createBuildCache()

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message == "Instant execution doesn't support the build cache yet."
    }
}
