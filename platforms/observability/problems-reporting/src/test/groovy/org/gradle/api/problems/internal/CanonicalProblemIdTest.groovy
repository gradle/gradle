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

package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import spock.lang.Shared
import spock.lang.Specification

class CanonicalProblemIdTest extends Specification {

    @Shared
    ProblemGroupsInternal groups = DefaultProblemGroups.INSTANCE

    def "factory group matching a predefined subgroup becomes the predefined subgroup"() {
        given:
        def id = ProblemId.create("Unused import", "Unused import", ProblemGroup.create("Java", "Java language", groups.compilation))

        when:
        def canonical = groups.canonical(id)

        then:
        canonical.group.is(groups.compilation.java)
        canonical.group.displayName == "Java"
        canonical.name == "Unused import"
        canonical.displayName == "Unused import"
    }

    def "factory chain matching predefined groups becomes the predefined chain"() {
        given:
        def compilation = ProblemGroup.create("Compilation", "Compilation")
        def id = ProblemId.create("Unused import", "Unused import", ProblemGroup.create("Java", "Java", compilation))

        expect:
        groups.canonical(id).group.is(groups.compilation.java)
    }

    def "factory Undefined group becomes the predefined Undefined group"() {
        given:
        def id = ProblemId.create("Unknown compiler", "Unknown compiler", ProblemGroup.create("Undefined", "Undefined", groups.compilation))

        expect:
        groups.canonical(id).group.is(groups.compilation.undefined)
    }

    def "unmatched factory group is attached to the predefined parents"() {
        given:
        def compilation = ProblemGroup.create("Compilation", "Compilation")
        def id = ProblemId.create("Bundle failed", "Bundle failed", ProblemGroup.create("KMP", "Kotlin Multiplatform", compilation))

        when:
        def canonical = groups.canonical(id)

        then:
        canonical.group == id.group
        canonical.group.displayName == "Kotlin Multiplatform"
        canonical.group.parent.is(groups.compilation)
        canonical.group.parent.description != null
    }

    def "unknown child of the closed Gradle root is attached to the predefined root"() {
        given:
        def gradle = ProblemGroup.create("Gradle", "Gradle")
        def id = ProblemId.create("Something", "Something", ProblemGroup.create("Custom", "Custom", gradle))

        when:
        def canonical = groups.canonical(id)

        then:
        canonical.group == id.group
        canonical.group.parent.is(groups.gradle)
    }

    def "id is returned unchanged when #description"() {
        expect:
        groups.canonical(id).is(id)

        where:
        description                                  | id
        "its group is predefined"                    | groups.compilation.java.problemId("Unused import")
        "its group is created through the hierarchy" | groups.transformation.group("KMP").problemId("Bundle failed")
        "its root is not predefined"                 | ProblemId.create("Something", "Something", ProblemGroup.create("Custom", "Custom"))
        "only its leaf group is not predefined"      | ProblemId.create("Something", "Something", ProblemGroup.create("Custom", "Custom", groups.compilation.java))
    }
}
