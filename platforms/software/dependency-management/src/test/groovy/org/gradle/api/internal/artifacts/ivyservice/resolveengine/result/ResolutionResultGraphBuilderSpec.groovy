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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultPrinter.printGraph
import static org.gradle.util.internal.CollectionUtils.first

class ResolutionResultGraphBuilderSpec extends Specification {

    def builder = new ResolutionResultGraphBuilder()

    def "builds basic graph"() {
        given:
        node("root")
        node("mid1")
        node("mid2")
        node("leaf1")
        node("leaf2")
        node("leaf3")
        node("leaf4")

        resolvedConf("root", [dep("root", "mid1"), dep("root", "mid2")])

        resolvedConf("mid1", [dep("mid1", "leaf1"), dep("mid1", "leaf2")])
        resolvedConf("mid2", [dep("mid2", "leaf3"), dep("mid2", "leaf4")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])
        resolvedConf("leaf3", [])
        resolvedConf("leaf4", [])

        when:
        def result = builder.getRoot(id("root"))

        then:
        printGraph(result) == """x:root:1
  x:mid1:1 [root]
    x:leaf1:1 [mid1]
    x:leaf2:1 [mid1]
  x:mid2:1 [root]
    x:leaf3:1 [mid2]
    x:leaf4:1 [mid2]
"""
    }

    def "graph with multiple dependents"() {
        given:
        node("a")
        node("b1")
        node("b2")
        node("b3")

        resolvedConf("a", [dep("a", "b1"), dep("a", "b2"), dep("a", "b3")])

        resolvedConf("b1", [dep("b1", "b2"), dep("b1", "b3")])
        resolvedConf("b2", [dep("b2", "b3")])
        resolvedConf("b3", [])

        when:
        def result = builder.getRoot(id("a"))

        then:
        printGraph(result) == """x:a:1
  x:b1:1 [a]
    x:b2:1 [a,b1]
      x:b3:1 [a,b1,b2]
  x:b2:1 [a,b1]
    x:b3:1 [a,b1,b2]
  x:b3:1 [a,b1,b2]
"""
    }

    def "builds graph with cycles"() {
        given:
        node("a")
        node("b")
        node("c")
        resolvedConf("a", [dep("a", "b")])
        resolvedConf("b", [dep("b", "c")])
        resolvedConf("c", [dep("c", "a")])

        when:
        def result = builder.getRoot(id("a"))

        then:
        printGraph(result) == """x:a:1
  x:b:1 [a]
    x:c:1 [b]
      x:a:1 [c]
"""
    }

    def "includes selection reason"() {
        given:
        node("a")
        node("b", ComponentSelectionReasons.of(ComponentSelectionReasons.FORCED))
        node("c", ComponentSelectionReasons.of(ComponentSelectionReasons.CONFLICT_RESOLUTION))
        node("d")
        resolvedConf("a", [dep("a", "b"), dep("a", "c"), dep("a", "d", new RuntimeException("Boo!"))])
        resolvedConf("b", [])
        resolvedConf("c", [])
        resolvedConf("d", [])

        when:
        def deps = builder.getRoot(id("a")).dependencies

        then:
        def b = deps.find { it.selected.id.module == 'b' }
        def c = deps.find { it.selected.id.module == 'c' }

        b.selected.selectionReason.forced
        c.selected.selectionReason.conflictResolution
    }

    def "links dependents correctly"() {
        given:
        node("a")
        node("b")
        node("c")
        resolvedConf("a", [dep("a", "b")])
        resolvedConf("b", [dep("b", "c")])
        resolvedConf("c", [dep("c", "a")])

        when:
        def a = builder.getRoot(id("a"))

        then:
        def b  = first(a.dependencies).selected
        def c  = first(b.dependencies).selected
        def a2 = first(c.dependencies).selected

        a2.is(a)

        first(b.dependents).is(first(a.dependencies))
        first(c.dependents).is(first(b.dependencies))
        first(a.dependents).is(first(c.dependencies))

        first(b.dependents).from.is(a)
        first(c.dependents).from.is(b)
        first(a.dependents).from.is(c)
    }

    def "accumulates and avoids duplicate dependencies"() {
        given:
        node("root")
        node("mid1")
        node("leaf1")
        node("leaf2")

        resolvedConf("root", [dep("root", "mid1")])

        resolvedConf("mid1", [dep("mid1", "leaf1")])
        resolvedConf("mid1", [dep("mid1", "leaf1")]) //dupe
        resolvedConf("mid1", [dep("mid1", "leaf2")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])

        when:
        def result = builder.getRoot(id("root"))

        then:
        printGraph(result) == """x:root:1
  x:mid1:1 [root]
    x:leaf1:1 [mid1]
    x:leaf2:1 [mid1]
"""
    }

    def "accumulates and avoids duplicate unresolved dependencies"() {
        given:
        node("root")
        node("mid1")
        node("leaf1")
        node("leaf2")
        resolvedConf("root", [dep("root", "mid1")])

        resolvedConf("mid1", [dep("mid1", "leaf1", new RuntimeException("foo!"))])
        resolvedConf("mid1", [dep("mid1", "leaf1", new RuntimeException("bar!"))]) //dupe
        resolvedConf("mid1", [dep("mid1", "leaf2", new RuntimeException("baz!"))])

        when:
        def result = builder.getRoot(id("root"))

        then:
        def mid1 = first(result.dependencies) as ResolvedDependencyResult
        mid1.selected.dependencies.size() == 2
        mid1.selected.dependencies*.requested.module == ['leaf1', 'leaf2']
    }

    def "graph includes unresolved deps"() {
        given:
        node("a")
        node("b")
        node("c")
        resolvedConf("a", [dep("a", "b"), dep("a", "c"), dep("a", "U", new RuntimeException("unresolved!"))])
        resolvedConf("b", [])
        resolvedConf("c", [])

        when:
        def result = builder.getRoot(id("a"))

        then:
        printGraph(result) == """x:a:1
  x:b:1 [a]
  x:c:1 [a]
  x:U:1 -> x:U:1 - Could not resolve x:U:1.
"""
    }

    private void node(String module, ComponentSelectionReason reason = ComponentSelectionReasons.requested()) {
        DummyModuleVersionSelection moduleVersion = comp(module, reason)
        builder.startVisitComponent(moduleVersion.resultId, moduleVersion.selectionReason, "repo")
        builder.visitComponentDetails(moduleVersion.componentId, moduleVersion.moduleVersion)
        builder.visitSelectedVariant(moduleVersion.resultId, Stub(ResolvedVariantResult))
        builder.visitComponentVariants([])
        builder.endVisitComponent()
    }

    private DummyModuleVersionSelection comp(String module, ComponentSelectionReason reason = ComponentSelectionReasons.requested()) {
        def moduleVersion = new DummyModuleVersionSelection(resultId: id(module), moduleVersion: newId(DefaultModuleIdentifier.newId("x", module), "1"), selectionReason: reason, componentId: new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId("x", module), "1"))
        moduleVersion
    }

    private void resolvedConf(String module, List<ResolvedGraphDependency> deps) {
        builder.visitOutgoingEdges(id(module), deps)
    }

    private ResolvedGraphDependency dep(String from, String requested, Exception failure = null, String selected = requested) {
        def selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("x", requested), DefaultImmutableVersionConstraint.of("1"))
        def moduleVersionSelector = newSelector(DefaultModuleIdentifier.newId("x", requested), "1")
        failure = failure == null ? null : new ModuleVersionResolveException(moduleVersionSelector, failure)
        return Stub(ResolvedGraphDependency) {
            getRequested() >> selector
            getFromVariant() >> id(from)
            getSelected() >> id(selected)
            getSelectedVariant() >> id(selected)
            getFailure() >> failure
        }
    }

    private Long id(String module) {
        return module.hashCode()
    }

    class DummyModuleVersionSelection implements ResolvedGraphComponent {
        long resultId
        ModuleVersionIdentifier moduleVersion
        ComponentSelectionReason selectionReason
        ComponentIdentifier componentId
        List<ResolvedVariantResult> selectedVariants = []
        String repositoryName

        @Override
        ComponentGraphResolveState getResolveState() {
            throw new UnsupportedOperationException()
        }
    }
}
