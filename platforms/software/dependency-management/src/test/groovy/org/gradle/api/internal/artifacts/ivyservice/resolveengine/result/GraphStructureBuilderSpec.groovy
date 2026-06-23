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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.jspecify.annotations.Nullable
import spock.lang.Specification

import static GraphStructurePrinter.printGraph

/**
 * Tests {@link GraphStructureBuilder}.
 */
class GraphStructureBuilderSpec extends Specification {

    def builder = new GraphStructureBuilder()

    def "builds basic graph"() {
        given:
        builder.start(id("root"))
        node("root", [dep("mid1"), dep("mid2")])
        node("mid1", [dep("leaf1"), dep("leaf2")])
        node("mid2", [dep("leaf3"), dep("leaf4")])
        node("leaf1", [])
        node("leaf2", [])
        node("leaf3", [])
        node("leaf4", [])

        when:
        def result = builder.build()

        then:
        printGraph(result) == """x:root:1
  x:mid1:1
    x:leaf1:1
    x:leaf2:1
  x:mid2:1
    x:leaf3:1
    x:leaf4:1
"""
    }

    def "graph with multiple dependents"() {
        given:
        builder.start(id("a"))
        node("a", [dep("b1"), dep("b2"), dep("b3")])
        node("b1", [dep("b2"), dep("b3")])
        node("b2", [dep("b3")])
        node("b3", [])

        when:
        def result = builder.build()

        then:
        printGraph(result) == """x:a:1
  x:b1:1
    x:b2:1
      x:b3:1
    x:b3:1 (*)
  x:b2:1 (*)
  x:b3:1 (*)
"""
    }

    def "builds graph with cycles"() {
        given:
        builder.start(id("a"))
        node("a", [dep("b")])
        node("b", [dep("c")])
        node("c", [dep("a")])

        when:
        def result = builder.build()

        then:
        printGraph(result) == """x:a:1
  x:b:1
    x:c:1
      x:a:1 (*)
"""
    }

    def "includes selection reason"() {
        given:
        builder.start(id("a"))
        node("a", [dep("b"), dep("c"), dep("d", new RuntimeException("Boo!"))])
        node("b", [], ComponentSelectionReasons.of(ComponentSelectionReasons.FORCED))
        node("c", [], ComponentSelectionReasons.of(ComponentSelectionReasons.CONFLICT_RESOLUTION))
        node("d", [])

        when:
        def result = builder.build()

        then:
        result.components().selectionReason(1).forced
        result.components().selectionReason(2).conflictResolution
    }

    def "links dependents correctly"() {
        given:
        builder.start(id("a"))
        node("a", [dep("b")])
        node("b", [dep("c")])
        node("c", [dep("a")])

        when:
        def result = builder.build()

        then:
        result.edges().targetNode(0) == 1
        result.edges().targetNode(1) == 2
        result.edges().targetNode(2) == 0
    }

    def "accumulates and handles duplicate dependencies"() {
        given:
        builder.start(id("root"))
        node("root", [dep("mid1")])

        node("mid1", [
            dep("leaf1"),
            dep("leaf1"), //dupe
            dep("leaf2")
        ])

        node("leaf1", [])
        node("leaf2", [])

        when:
        def result = builder.build()

        then:
        printGraph(result) == """x:root:1
  x:mid1:1
    x:leaf1:1
    x:leaf1:1 (*)
    x:leaf2:1
"""
    }

    def "graph includes unresolved deps"() {
        given:
        builder.start(id("a"))
        node("a", [dep("b"), dep("c"), dep("U", new RuntimeException("unresolved!"))])
        node("b", [])
        node("c", [])

        when:
        def result = builder.build()

        then:
        printGraph(result) == """x:a:1
  x:b:1
  x:c:1
  x:U:1 -> x:U:1 - Could not resolve x:U:1.
"""
    }

    private void node(String module, List<Edge> edges, ComponentSelectionReasonInternal reason = ComponentSelectionReasons.requested()) {
        def resultId = id(module)
        def componentId = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId("x", module), "1")
        def moduleVersionId = DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId("x", module), "1")
        builder.addComponent(resultId, reason, "repo", componentId, moduleVersionId)
        builder.addNode(resultId, resultId, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, module, -1)

        edges.each {
            def target = id(it.target)
            def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("x", it.target), "1")
            if (it.failure != null) {
                builder.addFailedEdge(selector, false, ComponentSelectionReasons.requested(), new ModuleVersionResolveException(selector, it.failure))
            } else {
                builder.addSuccessfulEdge(selector, false, target)
            }
        }
    }

    private Edge dep(String requested, Exception failure = null) {
        new Edge(requested, failure)
    }

    private Long id(String module) {
        return module.hashCode()
    }

    static record Edge(
        String target,
        @Nullable Exception failure
    ) { }

}
