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

package org.gradle.problems.internal.emitters

import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class ConsoleProblemEmitterTest extends Specification {

    @Shared
    ProblemsInternal problems = TestUtil.problemsService()

    def "renders problems reported into predefined groups, including Compilation > Java"() {
        expect:
        ConsoleProblemEmitter.shouldRender(problem(id))

        where:
        id << [
            problems.groups.compilation.kotlin.problemId("Unused import"),
            problems.groups.compilation.java.problemId("Unused import"),
            problems.groups.compilation.java.group("Lint").problemId("Unused import"),
            problems.groups.compilation.java.problemId("Compiler initialization failed"),
        ]
    }

    def "does not render problems the producer has already written to the console"() {
        given:
        def problem = problems.internalReporter.internalCreate {
            it.id(problems.groups.compilation.java.problemId("Cannot find symbol"))
            it.writtenToConsole()
        }

        expect:
        !ConsoleProblemEmitter.shouldRender(problem)
    }

    def "does not render deprecations, which have their own console output"() {
        expect:
        !ConsoleProblemEmitter.shouldRender(problem(ProblemId.create("deprecated-feature", "Deprecated feature", GradleCoreProblemGroup.deprecation())))
    }

    private ProblemInternal problem(ProblemId id) {
        problems.internalReporter.create(id) {} as ProblemInternal
    }
}
