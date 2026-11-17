/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

/**
 * Tests {@code Task.onlyIf(Provider)} and {@code Task.setOnlyIf(Provider)} with {@code Boolean} and {@code OnlyIfDecision} values.
 */
class TaskOnlyIfProviderIntegrationTest extends AbstractIntegrationSpec {
    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def 'task with #condition runs'() {
        buildFile("""
            tasks.register("task") {
                $condition
                doLast { println("task ran") }
            }
        """)

        when:
        succeeds(":task")

        then:
        executedAndNotSkipped(":task")
        outputContains("task ran")

        where:
        condition << [
            "onlyIf(providers.provider { true })",
            "onlyIf(providers.provider { OnlyIfDecision.proceed() })",
            "onlyIf { false }\nsetOnlyIf(providers.provider { true })",
        ]
    }

    def 'task with #condition is skipped with "#reason"'() {
        buildFile("""
            tasks.register("task") {
                $condition
                doLast { println("task ran") }
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        assertSkipped(":task", reason)
        outputDoesNotContain("task ran")

        where:
        condition                                                               | reason
        "onlyIf(providers.provider { false })"                                  | "onlyIf not satisfied - no reason provided"
        "onlyIf(providers.provider { OnlyIfDecision.skip() })"                  | "onlyIf not satisfied - no reason provided"
        "onlyIf(providers.provider { OnlyIfDecision.skip('') })"                | "onlyIf not satisfied - no reason provided"
        "onlyIf(providers.provider { OnlyIfDecision.skip('not today') })"       | "not today"
        "setOnlyIf(providers.provider { OnlyIfDecision.skip('replaced') })"     | "replaced"
        "onlyIf(providers.provider { true })\n" +
            "onlyIf(providers.provider { OnlyIfDecision.skip('second') })"      | "second"
        "onlyIf(providers.provider { OnlyIfDecision.skip('first') })\n" +
            "onlyIf(providers.provider { OnlyIfDecision.skip('second') })"      | "first"
    }

    def 'reports the reason of the first unsatisfied predicate when mixed with Spec predicates (#condition)'() {
        buildFile("""
            tasks.register("task") {
                $condition
                doLast {}
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        assertSkipped(":task", reason)

        where:
        condition                                                               | reason
        "onlyIf('spec reason') { false }\n" +
            "onlyIf(providers.provider { OnlyIfDecision.skip('decision') })"    | "'spec reason' not satisfied"
        "onlyIf('spec reason') { true }\n" +
            "onlyIf(providers.provider { OnlyIfDecision.skip('decision') })"    | "decision"
        "onlyIf(providers.provider { OnlyIfDecision.skip('decision') })\n" +
            "onlyIf('spec reason') { false }"                                   | "decision"
        "onlyIf(providers.provider { true })\n" +
            "onlyIf('spec reason') { false }"                                   | "'spec reason' not satisfied"
        "onlyIf(providers.provider { OnlyIfDecision.skip('decision') })\n" +
            "setOnlyIf('spec reason') { false }"                                | "'spec reason' not satisfied"
    }

    def 'logs the skip reason of a decision'() {
        buildFile("""
            tasks.register("task") {
                onlyIf(providers.provider { OnlyIfDecision.skip('not today') })
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        outputContains("Skipping task ':task' as task onlyIf is false: not today")
    }

    def 'fails when the onlyIf provider has no value'() {
        buildFile("""
            tasks.register("task") {
                onlyIf(providers.gradleProperty("undefined"))
                doLast {}
            }
        """)

        when:
        fails(":task")

        then:
        failure.assertHasDescription("Could not evaluate onlyIf predicate for task ':task'.")
        failure.assertHasCause("The onlyIf provider has no value. Cannot query the value of Gradle property 'undefined' because it has no value available.")
    }

    def 'fails when the onlyIf provider has a value of another type'() {
        buildFile("""
            tasks.register("task") {
                onlyIf(providers.provider { "yes" })
                doLast {}
            }
        """)

        when:
        fails(":task")

        then:
        failure.assertHasDescription("Could not evaluate onlyIf predicate for task ':task'.")
        failure.assertHasCause("The onlyIf provider must have a value of type java.lang.Boolean or org.gradle.api.tasks.OnlyIfDecision, but its value 'yes' is of type java.lang.String.")
    }

    def 'decision from zipped providers is made from both values (#a, #b)'() {
        buildFile("""
            tasks.register("task") {
                onlyIf(providers.gradleProperty("a").zip(providers.gradleProperty("b")) { a, b ->
                    a == b ? OnlyIfDecision.proceed() : OnlyIfDecision.skip("\$a != \$b")
                })
                doLast { println("task ran") }
            }
        """)

        when:
        executer.withArguments("-Pa=$a", "-Pb=$b")
        succeeds(":task")

        then:
        if (reason == null) {
            outputContains("task ran")
        } else {
            assertSkipped(":task", reason)
        }

        where:
        a   | b   | reason
        "x" | "x" | null
        "x" | "y" | "x != y"
    }

    def 'mapped ValueSource is evaluated at task execution time (#content)'() {
        buildFile("""
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class ReadDecision implements ValueSource<String, Params> {
                interface Params extends ValueSourceParameters {
                    RegularFileProperty getDecisionFile()
                }

                @Override
                String obtain() {
                    def decisionFile = parameters.decisionFile.get().asFile
                    return decisionFile.exists() ? decisionFile.text : "missing"
                }
            }

            def decisionFile = layout.buildDirectory.file("decision.txt")
            def generate = tasks.register("generate") {
                outputs.file(decisionFile)
                doLast { decisionFile.get().asFile.text = "$content" }
            }

            tasks.register("task") {
                dependsOn(generate)
                onlyIf(providers.of(ReadDecision) { parameters.decisionFile.set(decisionFile) }.map {
                    it == "run" ? OnlyIfDecision.proceed() : OnlyIfDecision.skip("decision was '\$it'")
                })
                doLast { println("task ran") }
            }
        """)

        when:
        succeeds(":task")

        then:
        if (reason == null) {
            outputContains("task ran")
        } else {
            assertSkipped(":task", reason)
        }

        when:
        succeeds(":task")

        then:
        if (reason == null) {
            outputContains("task ran")
        } else {
            assertSkipped(":task", reason)
        }

        where:
        content | reason
        "run"   | null
        "skip"  | "decision was 'skip'"
    }

    def 'ValueSource-backed onlyIf provider is obtained once per build (#provider)'() {
        def obtainLog = file("obtain-log.txt")
        buildFile("""
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class CountingSource implements ValueSource<Boolean, Params> {
                interface Params extends ValueSourceParameters {
                    RegularFileProperty getObtainLog()
                }

                @Override
                Boolean obtain() {
                    parameters.obtainLog.get().asFile << "obtained\\n"
                    return false
                }
            }

            def source = providers.of(CountingSource) { parameters.obtainLog.set(file('${obtainLog.name}')) }

            tasks.register("task") {
                onlyIf($provider)
                doLast {}
            }
        """)

        when:
        succeeds(":task")

        then:
        assertSkipped(":task", reason)
        obtainLog.readLines() == ["obtained"]

        when:
        obtainLog.delete()
        succeeds(":task")

        then:
        assertSkipped(":task", reason)
        obtainLog.readLines() == ["obtained"]

        where:
        provider                                                                       | reason
        "source"                                                                       | "onlyIf not satisfied - no reason provided"
        "source.map { it ? OnlyIfDecision.proceed() : OnlyIfDecision.skip('mapped') }" | "mapped"
    }

    private void assertSkipped(String taskPath, String skipReasonMessage) {
        result.assertTaskSkipped(taskPath)
        def operation = operations.only(ExecuteTaskBuildOperationType) { it.details.taskPath == taskPath }
        assert operation.result.skipMessage == "SKIPPED"
        assert operation.result.skipReasonMessage == skipReasonMessage
    }
}
