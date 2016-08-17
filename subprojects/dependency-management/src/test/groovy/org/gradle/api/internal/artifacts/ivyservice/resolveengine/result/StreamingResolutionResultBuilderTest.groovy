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

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultPrinter.printGraph
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.*

class StreamingResolutionResultBuilderTest extends Specification {

    StreamingResolutionResultBuilder builder = new StreamingResolutionResultBuilder(new DummyBinaryStore(), new DummyStore())

    def "result can be read multiple times"() {
        def rootNode = node(1, "org", "root", "1.0", ROOT)
        builder.start(rootNode)
        builder.visitNode(rootNode)
        builder.finish(rootNode)

        when:
        def result = builder.complete()

        then:
        with(result) {
            root.id == DefaultModuleComponentIdentifier.newId("org", "root", "1.0")
            root.selectionReason == ROOT
        }
        printGraph(result.root) == """org:root:1.0
"""
    }

    def "maintains graph in byte stream"() {
        def root = node(1, "org", "root", "1.0", ROOT)
        def dep1 = node(2, "org", "dep1", "2.0", CONFLICT_RESOLUTION)
        root.outgoingEdges >> [
                dep("org", "dep1", "2.0", 2),
                dep("org", "dep2", "3.0", new RuntimeException("Boo!"))
        ]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(dep1)

        builder.visitEdge(root)

        builder.finish(root)

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0(C) [root]
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "visiting resolved module version again has no effect"() {
        def root = node(1, "org", "root", "1.0")
        root.outgoingEdges >> [dep("org", "dep1", "2.0", 2)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(1, "org", "root", "1.0", REQUESTED)) //it's fine

        builder.visitNode(node(2, "org", "dep1", "2.0", CONFLICT_RESOLUTION))
        builder.visitNode(node(2, "org", "dep1", "2.0", REQUESTED)) //will be ignored

        builder.visitEdge(root)

        builder.finish(root)

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0(C) [root]
"""
    }

    def "accumulates dependencies for all configurations of same component"() {
        def root = node(1, "org", "root", "1.0")
        root.outgoingEdges >> [dep("org", "dep1", "1.0", 2)]

        def conf1 = node(2, "org", "dep1", "1.0")
        conf1.outgoingEdges >> [dep("org", "dep2", "1.0", 3)]

        def conf2 = node(2, "org", "dep1", "1.0")
        conf2.outgoingEdges >> [dep("org", "dep3", "1.0", 4)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(conf1)
        builder.visitNode(conf2)
        builder.visitNode(node(3, "org", "dep2", "1.0"))
        builder.visitNode(node(4, "org", "dep3", "1.0"))

        builder.visitEdge(root)
        builder.visitEdge(conf1)
        builder.visitEdge(conf2)

        builder.finish(root)

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:1.0 [root]
    org:dep2:1.0 [dep1]
    org:dep3:1.0 [dep1]
"""
    }

    def "dependency failures are remembered"() {
        def root = node(1, "org", "root", "1.0")
        root.outgoingEdges >> [
                dep("org", "dep1", "1.0", new RuntimeException()),
                dep("org", "dep2", "2.0", 3)
        ]
        def dep2 = node(3, "org", "dep2", "2.0")
        dep2.outgoingEdges >> [dep("org", "dep1", "5.0", new RuntimeException())]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(2, "org", "dep1", "2.0"))

        builder.visitNode(dep2)

        builder.visitEdge(root)
        builder.visitEdge(dep2)

        builder.finish(root)

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:1.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0 [root]
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }

    private DependencyGraphEdge dep(String org, String name, String ver, Long selectedId) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> DefaultModuleComponentSelector.newSelector(org, name, ver)
        _ * edge.selected >> selectedId
        _ * edge.failure >> null
        return edge
    }

    private DependencyGraphEdge dep(String org, String name, String ver, Throwable failure) {
        def requested = DefaultModuleComponentSelector.newSelector(org, name, ver)

        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> requested
        _ * edge.reason >> VersionSelectionReasons.REQUESTED
        _ * edge.failure >> new ModuleVersionResolveException(requested, failure)
        return edge
    }

    private DependencyGraphNode node(Long resultId, String org, String name, String ver, ComponentSelectionReason reason = VersionSelectionReasons.REQUESTED) {
        def component = Stub(DependencyGraphComponent)
        _ * component.resultId >> resultId
        _ * component.moduleVersion >> DefaultModuleVersionIdentifier.newId(org, name, ver)
        _ * component.componentId >> DefaultModuleComponentIdentifier.newId(org, name, ver)
        _ * component.selectionReason >> reason

        def node = Stub(DependencyGraphNode)
        _ * node.owner >> component
        return node
    }
}
