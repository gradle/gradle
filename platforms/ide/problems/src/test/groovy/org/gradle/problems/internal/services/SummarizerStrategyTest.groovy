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
import org.gradle.api.problems.Severity
import org.gradle.api.problems.internal.DefaultProblem
import org.gradle.api.problems.internal.DefaultProblemDefinition
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
            null
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
                results.add(strategy.shouldEmit(problem))
            }
            finished()
        }

        then:
        results.size() == repeatitions * parallelExecutions
        results.findAll { it == true }.size() == repeatitions
        results.findAll { it == false }.size() == repeatitions * (parallelExecutions - 1)
    }
}
