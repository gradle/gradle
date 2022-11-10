/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.graph

import org.gradle.api.tasks.diagnostics.internal.graph.nodes.SimpleDependency
import org.gradle.internal.graph.GraphRenderer
import org.gradle.internal.logging.text.TestStyledTextOutput
import spock.lang.Specification

class DependencyGraphRendererSpec extends Specification {

    private textOutput = new TestStyledTextOutput().ignoreStyle()
    private graphRenderer = new GraphRenderer(textOutput)
    private renderer = new DependencyGraphsRenderer(textOutput, graphRenderer, NodeRenderer.NO_OP, new SimpleNodeRenderer())

    def "renders graph"() {
        def root = new SimpleDependency("root")
        def dep1 = new SimpleDependency("dep1")
        def dep11 = new SimpleDependency("dep1.1")
        def dep2 = new SimpleDependency("dep2")
        def dep21 = new SimpleDependency("dep2.1")
        def dep22 = new SimpleDependency("dep2.2")

        root.children.addAll(dep1, dep2)
        dep1.children.addAll(dep11)
        dep2.children.addAll(dep21, dep22)

        when:
        renderer.render([root])
        renderer.complete()

        then:
        textOutput.value.readLines() == [
                '+--- dep1',
                '|    \\--- dep1.1',
                '\\--- dep2',
                '     +--- dep2.1',
                '     \\--- dep2.2'
        ]
    }

    def "renders graph with repeated nodes"() {
        def root = new SimpleDependency("root")
        def dep1 = new SimpleDependency("dep1")
        def dep11 = new SimpleDependency("dep1.1")
        def dep2 = new SimpleDependency("dep2")
        def dep22 = new SimpleDependency("dep2.2")

        root.children.addAll(dep1, dep2)
        dep1.children.addAll(dep11)
        dep2.children.addAll(dep1, dep22)

        when:
        renderer.render([root])
        renderer.complete()

        then:
        textOutput.value.readLines() == [
                '+--- dep1',
                '|    \\--- dep1.1',
                '\\--- dep2',
                '     +--- dep1 (*)',
                '     \\--- dep2.2',
                '',
                '(*) - Indicates repeated occurrences of a transitive dependency subtree. Gradle expands transitive dependency subtrees only once per project; repeat occurrences only display the root of the subtree, followed by this annotation.'
        ]
    }
}
