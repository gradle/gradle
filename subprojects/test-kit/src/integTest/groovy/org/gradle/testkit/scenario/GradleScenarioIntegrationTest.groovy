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

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess


class GradleScenarioIntegrationTest extends AbstractGradleScenarioIntegrationTest {

    def "can create and run single step scenario on empty build with default tasks"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->
                steps.named("single")
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep("single").task(":help").outcome == TaskOutcome.SUCCESS
    }

    def "can create and run a multi-step scenario"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->

                steps.named("first")
                    .withResult { result ->
                        assert result.task(":help").outcome == TaskOutcome.SUCCESS
                    }

                steps.named("second")
                    .withResult { result ->
                        assert result.task(":help").outcome == TaskOutcome.SUCCESS
                    }
            }


        when:
        def result = scenario.run()

        then:
        result.ofStep("first").task(":help").outcome == TaskOutcome.SUCCESS
        result.ofStep("second").task(":help").outcome == TaskOutcome.SUCCESS
    }

    def "unexpected step failure stops scenario"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("help")

                steps.named("second")
                    .withArguments("doesNotExists")
            }

        when:
        scenario.run()

        then:
        def ex = thrown(UnexpectedScenarioStepFailure)
        ex.step == "second"
        ex.buildResult.output.contains("Task 'doesNotExists' not found in root project 'test'.")

        and:
        ex.cause instanceof UnexpectedBuildFailure
    }

    def "unexpected step success stops scenario run"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("help")

                steps.named("second")
                    .withArguments("help")
                    .withFailure()
            }

        when:
        scenario.run()

        then:
        def ex = thrown(UnexpectedScenarioStepSuccess)
        ex.step == "second"
        ex.buildResult.task(":help").outcome == TaskOutcome.SUCCESS

        and:
        ex.cause instanceof UnexpectedBuildSuccess
    }

    def "expected step failure does not stop scenario"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(emptyBuildWorkspace)
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("help")

                steps.named("second")
                    .withArguments("doesNotExists")
                    .withFailure()

                steps.named("third")
                    .withArguments("help")
            }

        when:
        def result = scenario.run()

        then:
        noExceptionThrown()
        result.ofStep('first').task(":help").outcome == TaskOutcome.SUCCESS
        result.ofStep('second').output.contains("Task 'doesNotExists' not found in root project 'test'.")
        result.ofStep('third').task(":help").outcome == TaskOutcome.SUCCESS
    }

    def "subsequent steps are run in the same workspace directory"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                new File(root, 'settings.gradle') << 'rootProject.name = "test"'
                new File(root, 'build.gradle') << 'println("projectDir=\${projectDir}")'
            }
            .withSteps { steps ->
                steps.named("first")
                steps.named("second")
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep("first").output.count("projectDir=") == 1
        result.ofStep("second").output.count("projectDir=") == 1

        and:
        def firstPath = result.ofStep("first").output.readLines().find { it.startsWith("projectDir=") }
        def secondPath = result.ofStep("second").output.readLines().find { it.startsWith("projectDir=") }
        firstPath == secondPath
    }

    def "can request a step to clean the workspace directory"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                new File(root, 'settings.gradle') << 'rootProject.name = "test"'
                new File(root, 'build.gradle') << '''
                    println("projectDir=\${projectDir}")
                    tasks.register("check") {
                        def tag = file("tag")
                        doLast {
                            assert !tag.exists()
                            tag.text = "tag"
                        }
                    }
                '''
            }
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("check")

                steps.named("second")
                    .withCleanWorkspace()
                    .withArguments("check")
            }

        when:
        def result = scenario.run()

        then:
        def firstPath = result.ofStep("first").output.readLines().find { it.startsWith("projectDir=") }
        def secondPath = result.ofStep("second").output.readLines().find { it.startsWith("projectDir=") }
        firstPath == secondPath
    }

    def "can request a step to relocate the workspace directory"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                new File(root, 'settings.gradle') << 'rootProject.name = "test"'
                new File(root, 'build.gradle') << '''
                    println("projectDir=\${projectDir}")
                    def tag = file("tag")
                    tasks.register("createTag") {
                        doLast {
                            assert !tag.exists()
                            tag.text = "tag"
                        }
                    }
                    tasks.register("readTag") {
                        doLast {
                            assert tag.exists()
                            println(tag.text)
                        }
                    }
                '''
            }
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("createTag")

                steps.named("second")
                    .withRelocatedWorkspace()
                    .withArguments("readTag")
            }

        when:
        def result = scenario.run()

        then:
        def firstPath = result.ofStep("first").output.readLines().find { it.startsWith("projectDir=") }
        def secondPath = result.ofStep("second").output.readLines().find { it.startsWith("projectDir=") }
        firstPath != secondPath
    }


    def "can request a step to relocate and clean the workspace directory"() {

        given:
        def scenario = GradleScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                new File(root, 'settings.gradle') << 'rootProject.name = "test"'
                new File(root, 'build.gradle') << '''
                    println("projectDir=\${projectDir}")
                    tasks.register("check") {
                        def tag = file("tag")
                        doLast {
                            assert !tag.exists()
                            tag.text = "tag"
                        }
                    }
                '''
            }
            .withSteps { steps ->

                steps.named("first")
                    .withArguments("check")

                steps.named("second")
                    .withRelocatedWorkspace()
                    .withCleanWorkspace()
                    .withArguments("check")
            }

        when:
        def result = scenario.run()

        then:
        def firstPath = result.ofStep("first").output.readLines().find { it.startsWith("projectDir=") }
        def secondPath = result.ofStep("second").output.readLines().find { it.startsWith("projectDir=") }
        firstPath != secondPath
    }
}
