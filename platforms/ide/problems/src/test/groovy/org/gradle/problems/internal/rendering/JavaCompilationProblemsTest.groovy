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

package org.gradle.problems.internal.rendering

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemGroups
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class JavaCompilationProblemsTest extends Specification {

    @Shared
    ProblemGroups groups = TestUtil.problemsService().groups

    def "identifies the predefined Java compilation group"() {
        expect:
        JavaCompilationProblems.isJavaCompilationGroup(groups.compilation.java)
        JavaCompilationProblems.isInJavaCompilationGroup(groups.compilation.java)
    }

    def "identifies sub-groups of the Java compilation group as being in it"() {
        expect:
        !JavaCompilationProblems.isJavaCompilationGroup(group)
        JavaCompilationProblems.isInJavaCompilationGroup(group)

        where:
        group << [groups.compilation.java.undefined, groups.compilation.java.group("Lint")]
    }

    def "does not identify other groups"() {
        expect:
        !JavaCompilationProblems.isJavaCompilationGroup(group)
        !JavaCompilationProblems.isInJavaCompilationGroup(group)

        where:
        group << [
            groups.compilation,
            groups.compilation.kotlin,
            groups.compilation.group("java"),
            groups.transformation.group("Java"),
            groups.gradle.dslEvaluation,
        ]
    }

    def "identification is structural"() {
        expect:
        JavaCompilationProblems.isJavaCompilationGroup(ProblemGroup.create("Java", "Java compilation", ProblemGroup.create("Compilation", "Compilation")))
        !JavaCompilationProblems.isJavaCompilationGroup(ProblemGroup.create("Java", "Java", ProblemGroup.create("Compilation", "Compilation", ProblemGroup.create("Root", "Root"))))
    }
}
