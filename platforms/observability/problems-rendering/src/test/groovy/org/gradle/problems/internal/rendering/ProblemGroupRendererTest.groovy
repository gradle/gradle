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
import org.gradle.api.problems.ProblemId
import spock.lang.Specification

class ProblemGroupRendererTest extends Specification {

    def "renders the group chain from the root, and the id as name followed by its chain"() {
        def root = ProblemGroup.create("Root", "Root")
        def child = ProblemGroup.create("Child", "Child", root)
        def leaf = ProblemGroup.create("Leaf", "Leaf", child)

        expect:
        ProblemGroupRenderer.render(root) == "Root"
        ProblemGroupRenderer.render(child) == "Root > Child"
        ProblemGroupRenderer.render(leaf) == "Root > Child > Leaf"
        ProblemGroupRenderer.render(ProblemId.create("Unused import", "Unused import", child)) == "Unused import (in Root > Child)"
    }

    def "quotes group names that contain the separator or a quote so the rendering cannot be misread"() {
        def root = ProblemGroup.create("Root", "Root")
        def tricky = ProblemGroup.create("Java > Kotlin", "Java > Kotlin", root)
        def quoted = ProblemGroup.create('Say "hi"', 'Say "hi"', root)

        expect:
        ProblemGroupRenderer.render(tricky) == 'Root > "Java > Kotlin"'
        ProblemGroupRenderer.render(quoted) == 'Root > "Say \\"hi\\""'
        ProblemGroupRenderer.render(ProblemId.create("a > b", "a > b", tricky)) == '"a > b" (in Root > "Java > Kotlin")'
    }

    def "problem names are quoted only when they contain the separator, since sentences legitimately contain quotes"() {
        def root = ProblemGroup.create("Root", "Root")

        expect:
        ProblemGroupRenderer.render(ProblemId.create('Class "Foo" is bad', 'Class "Foo" is bad', root)) == 'Class "Foo" is bad (in Root)'
        ProblemGroupRenderer.render(ProblemId.create("a > b (in c)", "a > b (in c)", root)) == '"a > b (in c)" (in Root)'
        ProblemGroupRenderer.render(ProblemId.create('Say "hi" > there', 'Say "hi" > there', root)) == '"Say \\"hi\\" > there" (in Root)'
    }

    def "quoteIfNeeded('#name') renders as #expected"() {
        expect:
        ProblemGroupRenderer.quoteIfNeeded(name) == expected

        where:
        name            | expected
        "plain"         | "plain"
        "has : colon"   | "has : colon"
        "has>no spaces" | '"has>no spaces"'
        'back\\slash'   | 'back\\slash'
        'back\\"quote'  | '"back\\\\\\"quote"'
        '"'             | '"\\""'
    }
}
