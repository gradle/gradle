/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

class TaskOnlyIfReasonIntegrationTest extends AbstractIntegrationSpec {
    def 'task skipped by #condition reports "#reason"'() {
        buildFile("""
            tasks.register("task") {
                $condition
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        assertTaskSkippedWithMessage(reason, ":task")

        where:
        condition                                                                           | reason
        "onlyIf('condition1') { false }"                                                    | "condition1"
        "onlyIf('...') { true }\nonlyIf('condition2') { false }"                            | "condition2"
        "onlyIf('...') { false }\nsetOnlyIf('condition3') { false }"                        | "condition3"
        "onlyIf { false }"                                                                  | "Task satisfies onlyIf closure"
        "onlyIf(new Spec<Task>() { boolean isSatisfiedBy(Task task) { return false } })"    | "Task satisfies onlyIf spec"
        "setOnlyIf { false }"                                                               | "Task satisfies onlyIf closure"
        "setOnlyIf(new Spec<Task>() { boolean isSatisfiedBy(Task task) { return false } })" | "Task satisfies onlyIf spec"
        "onlyIf(providers.provider { 'condition4' }) { false }"                             | "condition4"
        "onlyIf('...') { true }\nsetOnlyIf(providers.provider { 'condition5' }) { false }"  | "condition5"
        "onlyIf(providers.provider { 'first' }) { false }\n" +
            "onlyIf(providers.provider { 'second' }) { false }"                             | "first"
        "onlyIf(providers.provider { 'first' }) { true }\n" +
            "onlyIf(providers.provider { 'second' }) { false }"                             | "second"
        "onlyIf('first') { false }\n" +
            "onlyIf(providers.provider { 'second' }) { false }"                             | "first"
    }

    @Issue("https://github.com/gradle/gradle/issues/38488")
    def 'ValueSource-backed reason is not obtained when the task is not skipped'() {
        def marker = file("value-source-obtained.txt")
        buildFile("""
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class ExpensiveReason implements ValueSource<String, ValueSourceParameters.None> {
                @Override
                String obtain() {
                    new File('${marker.absolutePath.replace('\\', '/')}').text = 'obtained'
                    return 'never used'
                }
            }

            tasks.register("task") {
                onlyIf(providers.of(ExpensiveReason) {}) { true }
                doLast {}
            }
        """)

        when:
        succeeds(":task")

        then:
        !marker.exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/38488")
    def 'ValueSource-backed reason is obtained when the task is skipped'() {
        def marker = file("value-source-obtained.txt")
        buildFile("""
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class ExpensiveReason implements ValueSource<String, ValueSourceParameters.None> {
                @Override
                String obtain() {
                    new File('${marker.absolutePath.replace('\\', '/')}').text = 'obtained'
                    return 'lazy reason'
                }
            }

            tasks.register("task") {
                onlyIf(providers.of(ExpensiveReason) {}) { false }
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        assertTaskSkippedWithMessage("lazy reason", ":task")
        marker.exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/38488")
    def 'only failing onlyIf reason Providers are queried, in registration order (earlier=#earlierPasses, later=#laterPasses)'() {
        def earlierMarker = file("earlier-obtained.txt")
        def laterMarker = file("later-obtained.txt")
        buildFile("""
            import org.gradle.api.provider.ValueSource
            import org.gradle.api.provider.ValueSourceParameters

            abstract class MarkerReason implements ValueSource<String, Params> {
                interface Params extends ValueSourceParameters {
                    org.gradle.api.file.RegularFileProperty getMarker()
                    org.gradle.api.provider.Property<String> getReason()
                }
                @Override
                String obtain() {
                    parameters.marker.get().asFile.text = 'obtained'
                    return parameters.reason.get()
                }
            }

            def earlier = providers.of(MarkerReason) {
                parameters.marker.set(file('${earlierMarker.name}'))
                parameters.reason.set('earlier')
            }
            def later = providers.of(MarkerReason) {
                parameters.marker.set(file('${laterMarker.name}'))
                parameters.reason.set('later')
            }

            tasks.register("task") {
                onlyIf(earlier) { $earlierPasses }
                onlyIf(later) { $laterPasses }
                doLast {}
            }
        """)

        when:
        executer.withArgument("--info")
        succeeds(":task")

        then:
        if (expectedSkipReason == null) {
            outputDoesNotContain("Skipping task ':task'")
        } else {
            assertTaskSkippedWithMessage(expectedSkipReason, ":task")
        }
        assert earlierMarker.exists() == expectedEarlierObtained
        assert laterMarker.exists() == expectedLaterObtained

        where:
        earlierPasses | laterPasses || expectedSkipReason | expectedEarlierObtained | expectedLaterObtained
        true          | false       || "later"            | false                   | true
        false         | true        || "earlier"          | true                    | false
        true          | true        || null               | false                   | false
        false         | false       || "earlier"          | true                    | false
    }

    /**
     * A {@code providers.provider { ... }} reason is a {@code DefaultProvider}, which the configuration cache
     * documents as eagerly evaluated at store time (see {@code DefaultProvider} javadoc). This test pins that
     * behavior so a future change won't silently regress the documented contract — and so users who need true
     * CC laziness know to reach for {@link org.gradle.api.provider.ValueSource} instead.
     */
    @Issue("https://github.com/gradle/gradle/issues/38488")
    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "documents that DefaultProvider is eagerly evaluated by the configuration cache at store time")
    def 'closure-backed reason Provider is not queried when the task is not skipped (without configuration cache)'() {
        def marker = file("provider-evaluated.txt")
        buildFile("""
            def marker = file('${marker.name}')
            tasks.register("task") {
                onlyIf(providers.provider { marker.text = 'evaluated'; 'never used' }) { true }
                doLast {}
            }
        """)

        when:
        succeeds(":task")

        then:
        !marker.exists()
    }

    private void assertTaskSkippedWithMessage(
        String message,
        String taskPath
    ) {
        outputContains("Skipping task '$taskPath' as task onlyIf '$message' is false")
        operations.only(ExecuteTaskBuildOperationType, {
            if (taskPath != it.details.taskPath) {
                return false
            }
            it.result.skipReasonMessage == "'$message' not satisfied"
        })
    }

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)
}
