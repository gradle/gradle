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

package org.gradle.problems.internal.impl

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.internal.DefaultProblemGroups
import spock.lang.Specification

class ProblemGroupTableTest extends Specification {

    def groups = DefaultProblemGroups.INSTANCE
    def table = new ProblemGroupTable(groups.roots)

    def "lists groups depth first, roots in spec order, sub-groups by name ignoring case, Undefined last"() {
        given:
        table.add(groups.others.group("zeta").problemId("Problem"))
        table.add(groups.compilation.undefined.problemId("Problem"))
        table.add(groups.others.group("Alpha").problemId("Problem"))
        table.add(groups.compilation.java.problemId("Problem"))
        table.add(groups.compilation.group("assembly").problemId("Problem"))
        table.add(groups.dependencies.declaration.problemId("Problem"))

        expect:
        table.toJsProblemGroups()*.path == [
            ["Dependencies"],
            ["Dependencies", "Declaration"],
            ["Compilation"],
            ["Compilation", "assembly"],
            ["Compilation", "Java"],
            ["Compilation", "Undefined"],
            ["Others"],
            ["Others", "Alpha"],
            ["Others", "zeta"],
        ]
    }

    def "lists each group once"() {
        given:
        table.add(groups.compilation.java.problemId("One"))
        table.add(groups.compilation.java.problemId("Two"))
        table.add(groups.compilation.kotlin.problemId("Three"))

        expect:
        table.toJsProblemGroups()*.path == [["Compilation"], ["Compilation", "Java"], ["Compilation", "Kotlin"]]
    }

    def "predefined groups carry their description, user groups none"() {
        given:
        table.add(groups.compilation.java.problemId("Problem"))
        table.add(groups.compilation.undefined.problemId("Problem"))
        table.add(groups.others.group("My plugin").problemId("Problem"))

        when:
        def descriptions = table.toJsProblemGroups().collectEntries { [it.path, it.description] }

        then:
        descriptions[["Compilation"]] == groups.compilation.description
        descriptions[["Compilation", "Java"]] == groups.compilation.java.description
        descriptions[["Compilation", "Undefined"]] == groups.compilation.undefined.description
        descriptions[["Compilation", "Undefined"]] != null
        descriptions[["Others"]] == groups.others.description
        descriptions[["Others", "My plugin"]] == null
    }

    def "Undefined comes last even when a sibling sorts after it by name"() {
        given:
        table.add(groups.compilation.undefined.problemId("Problem"))
        table.add(groups.compilation.group("Zorglub").problemId("Problem"))

        expect:
        table.toJsProblemGroups()*.path == [["Compilation"], ["Compilation", "Zorglub"], ["Compilation", "Undefined"]]
    }

    def "Others is the last predefined root"() {
        expect:
        groups.roots.last() == groups.others
    }

    def "a legacy root comes after the predefined roots but before Others"() {
        given:
        table.add(legacyId("legacy"))
        table.add(groups.others.group("My plugin").problemId("Problem"))
        table.add(legacyId("Another legacy"))
        table.add(legacyId("Zorglub"))
        table.add(groups.dependencies.declaration.problemId("Problem"))

        expect:
        table.toJsProblemGroups()*.path == [
            ["Dependencies"],
            ["Dependencies", "Declaration"],
            ["Another legacy"],
            ["legacy"],
            ["Zorglub"],
            ["Others"],
            ["Others", "My plugin"],
        ]
    }

    private static ProblemId legacyId(String rootName) {
        ProblemId.create("problem", "Problem", ProblemGroup.create(rootName, rootName))
    }
}
