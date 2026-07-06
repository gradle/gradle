/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier

import static GraphStructurePrinter.printGraph

class StreamingResolutionResultBuilderTest extends AbstractResolutionResultBuilderTest {

    def "result can be read multiple times"() {
        def rootNode = rootNode("org", "root", "1.0")
        builder.start(rootNode)
        builder.visitNode(rootNode)
        builder.finish(rootNode)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        with(result.graphSource().get()) {
            def rootComponent = nodes().owner(nodes().root())
            components().id(rootComponent) == DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "root"), "1.0")
            components().selectionReason(rootComponent) == ComponentSelectionReasons.root()
        }
        printGraph(result.graphSource().get()) == """org:root:1.0
"""
    }

    def "maintains graph in byte stream"() {
        def node1 = node(component("org", "dep1", "2.0", ComponentSelectionReasons.of(ComponentSelectionReasons.CONFLICT_RESOLUTION)))
        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [
            dep(node1),
            dep(selector("org", "dep2", "3.0"), new RuntimeException("Boo!"))
        ]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node1)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:2.0(C)
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "accumulates dependencies for all configurations of same component"() {
        def node2 = node(component("org", "dep2", "1.0"))
        def node3 = node(component("org", "dep3", "1.0"))

        def comp1 = component("org", "dep1", "1.0")
        def node11 = node(comp1)
        def node12 = node(comp1)
        node11.outgoingEdges >> [dep(node2)]
        node12.outgoingEdges >> [dep(node3)]

        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [dep(node11), dep(node12)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node11)
        builder.visitNode(node12)
        builder.visitNode(node2)
        builder.visitNode(node3)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:1.0
    org:dep2:1.0
  org:dep1:1.0
    org:dep3:1.0
"""
    }

    def "dependency failures are remembered"() {
        def node2 = node(component("org", "dep2", "2.0"))
        node2.outgoingEdges >> [dep(selector("org", "dep1", "5.0"), new RuntimeException())]

        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [
            dep(selector("org", "dep1", "1.0"), new RuntimeException()),
            dep(node2)
        ]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(component("org", "dep1", "2.0")))

        builder.visitNode(node2)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:1.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }
}
