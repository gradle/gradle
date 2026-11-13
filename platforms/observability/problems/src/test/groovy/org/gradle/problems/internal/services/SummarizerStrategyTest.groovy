/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.problems.internal.services


import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemLocation
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemDefinition
import org.gradle.api.problems.internal.DefaultStackTraceLocation
import org.gradle.internal.deprecation.Documentation
import org.gradle.util.ConcurrentSpecification

class SummarizerStrategyTest extends ConcurrentSpecification {
    private static createTestProblem(String id) {
        new DefaultProblem(
            new DefaultProblemDefinition(
                ProblemId.create('message', "displayName", ProblemGroup.create(id, "Generic")),
                Severity.ERROR,
                Documentation.userManual('id')
            ),
            null,
            [],
            [],
            [],
            'description',
            new RuntimeException('cause'),
            null,
            false
        )
    }

    // No exception, so the hash codes of problems with the same details collide.
    private static createProblemWithoutException(String details = 'description', List<ProblemLocation> contextualLocations = []) {
        new DefaultProblem(
            new DefaultProblemDefinition(
                ProblemId.create('message', "displayName", ProblemGroup.create("id", "Generic")),
                Severity.ERROR,
                Documentation.userManual('id')
            ),
            null,
            [],
            [],
            contextualLocations,
            details,
            null,
            null,
            false
        )
    }

    def "should emit"() {
        when:
        def results = [].asSynchronized()


        def repeatitions = 100
        def parallelExecutions = 5
        def strategy = new SummarizerStrategy(4)

        repeatitions.times {
            def problem = createTestProblem("$it")
            startSynchronizedNTimes(parallelExecutions) {
                results.add(strategy.shouldEmit(problem, null))
            }
            finished()
        }

        then:
        results.size() == repeatitions * parallelExecutions
        results.findAll { it == true }.size() == repeatitions
        results.findAll { it == false }.size() == repeatitions * (parallelExecutions - 1)
    }

    def "identical problems reported from different tasks are both emitted"() {
        given:
        def strategy = new SummarizerStrategy(4)
        def problem = createProblemWithoutException()

        expect:
        strategy.shouldEmit(problem, ":compileDebugKotlin")
        strategy.shouldEmit(problem, ":compileReleaseKotlin")
    }

    def "identical problems reported from the same task are deduplicated"() {
        given:
        def strategy = new SummarizerStrategy(4)

        expect:
        strategy.shouldEmit(createProblemWithoutException(), ":compileDebugKotlin")
        !strategy.shouldEmit(createProblemWithoutException(), ":compileDebugKotlin")
    }

    def "identical problems reached through different stack traces are deduplicated"() {
        given:
        def strategy = new SummarizerStrategy(4)
        def first = createProblemWithoutException('description', [new DefaultStackTraceLocation(null, [new StackTraceElement("Outer", "report", "Outer.java", 1)])])
        def second = createProblemWithoutException('description', [new DefaultStackTraceLocation(null, [new StackTraceElement("Included", "report", "Included.java", 2)])])

        expect:
        strategy.shouldEmit(first, null)
        !strategy.shouldEmit(second, null)
    }

    def "the threshold applies per task"() {
        given:
        def strategy = new SummarizerStrategy(1)
        def problem = createProblemWithoutException()
        def otherProblem = createProblemWithoutException('other')

        expect: "any number of tasks can report the same problem"
        strategy.shouldEmit(problem, ":a")
        strategy.shouldEmit(problem, ":b")
        strategy.shouldEmit(problem, ":c")
        strategy.getCutOffProblems().empty

        and: "a task is cut off at the threshold"
        !strategy.shouldEmit(otherProblem, ":a")

        and: "another task is not affected"
        strategy.shouldEmit(otherProblem, ":d")
    }

    def "problems reported outside a task share one threshold"() {
        given:
        def strategy = new SummarizerStrategy(1)

        expect:
        strategy.shouldEmit(createProblemWithoutException(), null)
        !strategy.shouldEmit(createProblemWithoutException('other'), null)
    }

    def "the summary adds up what was cut off across tasks"() {
        given:
        def strategy = new SummarizerStrategy(1)
        def problem = createProblemWithoutException()

        when:
        [":a", ":b"].each { task ->
            strategy.shouldEmit(problem, task)
            strategy.shouldEmit(createProblemWithoutException('other'), task)
            strategy.shouldEmit(createProblemWithoutException('another'), task)
        }
        def cutOff = strategy.getCutOffProblems()

        then:
        cutOff.size() == 1
        cutOff.first().problemId == problem.definition.id
        cutOff.first().count == 4
    }
}
