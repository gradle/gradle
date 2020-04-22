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

import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.fixtures.GradleRunnerScenario


@GradleRunnerScenario
class GradleScenarioIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def "can create and run an ad-hoc scenario comprised of several steps"() {

        given:
        def scenario = GradleScenario.create()
            .withBaseDirectory(testDirectory.file("scenario-base-dir"))
            .withRunnerFactory {
                runner()
                    .forwardOutput()
                    .withArguments("-Dorg.gradle.unsafe.instant-execution=true")
            }
            .withWorkspace { root ->
                new File(root, "settings.gradle") << """rootProject.name = 'test'"""
                new File(root, "build.gradle") << '''
                    plugins {
                        id("java-library")
                    }
                '''
                new File(root, "src/main/java/Foo.java").tap {
                    parentFile.mkdirs()
                    text = 'public class Foo {}'
                }
            }
            .withSteps {

                it.named("store")
                    .withTasks("assemble")
                    .withResult {
                        assert it.task(":compileJava").outcome == TaskOutcome.SUCCESS
                    }

                it.named("load-up-to-date")
                    .withTasks("assemble")
                    .withResult {
                        assert it.task(":compileJava").outcome == TaskOutcome.UP_TO_DATE
                    }

                it.named("load-incremental")
                    .withTasks("assemble")
                    .withWorkspaceMutation { root ->
                        new File(root, "src/main/java/Foo.java").text = '''
                            public class Foo {
                                public void foo() {}
                            }
                        '''
                    }
                    .withResult {
                        assert it.task(":compileJava").outcome == TaskOutcome.SUCCESS
                    }

                it.named("clean")
                    .withTasks("clean")

                it.named("load-clean")
                    .withTasks("assemble")
                    .withResult {
                        assert it.task(":compileJava").outcome == TaskOutcome.SUCCESS
                    }
            }

        when:
        def result = scenario.run()

        then:
        result.ofStep("store").tap {
            assert task(":compileJava").outcome == TaskOutcome.SUCCESS
        }
        result.ofStep("load-up-to-date").tap {
            assert task(":compileJava").outcome == TaskOutcome.UP_TO_DATE
        }
        result.ofStep("load-clean").tap {
            assert task(":compileJava").outcome == TaskOutcome.SUCCESS
        }
    }

    def "build failure stops scenario run"() {

        given:
        def scenario = GradleScenario.create()
            .withBaseDirectory(testDirectory.file("scenario-base"))
            .withRunnerFactory { runner().forwardOutput() }
            .withWorkspace { file(it).file('settings.gradle') << '' }
            .withSteps {
                it.named("first").withTasks("help")
                it.named("second").withTasks("doesNotExists")
            }

        when:
        scenario.run()

        then:
        thrown(UnexpectedBuildFailure)
    }

    def "expected build failure does not stop scenario run"() {

        given:
        def scenario = GradleScenario.create()
            .withBaseDirectory(testDirectory.file("scenario-base"))
            .withRunnerFactory { runner().forwardOutput() }
            .withWorkspace { file(it).file('settings.gradle') << '' }
            .withSteps {
                it.named("first").withTasks("help")
                it.named("second").withTasks("doesNotExists").withFailure()
            }

        when:
        def result = scenario.run()

        then:
        noExceptionThrown()
        result.ofStep('first').task(":help").outcome == TaskOutcome.SUCCESS
        result.ofStep('second').output.contains("Task 'doesNotExists' not found in root project 'scenario-base'.")
    }
}
