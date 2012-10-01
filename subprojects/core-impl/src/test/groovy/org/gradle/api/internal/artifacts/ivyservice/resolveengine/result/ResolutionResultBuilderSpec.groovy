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
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 8/27/12
 */
class ResolutionResultBuilderSpec extends Specification {

    def builder = new ResolutionResultBuilder()

    def "builds basic graph"() {
        given:
        builder.start(confId("root"))
        resolvedConf("root", [dep("mid1"), dep("mid2")])

        resolvedConf("mid1", [dep("leaf1"), dep("leaf2")])
        resolvedConf("mid2", [dep("leaf3"), dep("leaf4")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])
        resolvedConf("leaf3", [])
        resolvedConf("leaf4", [])

        when:
        def result = builder.getResult()

        then:
        print(result.root) == """x:root:1
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
        builder.start(confId("a"))
        resolvedConf("a", [dep("b1"), dep("b2"), dep("b3")])

        resolvedConf("b1", [dep("b2"), dep("b3")])
        resolvedConf("b2", [dep("b3")])
        resolvedConf("b3", [])

        when:
        def result = builder.getResult()

        then:
        print(result.root) == """x:a:1
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
        builder.start(confId("a"))
        resolvedConf("a", [dep("b")])
        resolvedConf("b", [dep("c")])
        resolvedConf("c", [dep("a")])

        when:
        def result = builder.getResult()

        then:
        print(result.root) == """x:a:1
  x:b:1 [a]
    x:c:1 [b]
      x:a:1 [c]
"""
    }

    def "includes selection reason"() {
        given:
        builder.start(confId("a"))
        resolvedConf("a", [dep("b", null, "b", VersionSelectionReasons.FORCED), dep("c", null, "c", VersionSelectionReasons.CONFLICT_RESOLUTION), dep("d", new RuntimeException("Boo!"))])
        resolvedConf("b", [])
        resolvedConf("c", [])
        resolvedConf("d", [])

        when:
        def deps = builder.result.root.dependencies

        then:
        def b = deps.find { it.selected.id.name == 'b' }
        def c = deps.find { it.selected.id.name == 'c' }

        b.selected.selectionReason.forced
        c.selected.selectionReason.conflictResolution
    }

    def "links dependents correctly"() {
        given:
        builder.start(confId("a"))
        resolvedConf("a", [dep("b")])
        resolvedConf("b", [dep("c")])
        resolvedConf("c", [dep("a")])

        when:
        def a = builder.getResult().root

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

    ResolvedDependencyResult first(Set<? extends ResolvedDependencyResult> dependencies) {
        dependencies.iterator().next()
    }

    def "accumulates dependencies"() {
        given:
        builder.start(confId("root"))
        resolvedConf("root", [dep("mid1")])

        resolvedConf("mid1", [dep("leaf1")])
        resolvedConf("mid1", [dep("leaf2")])

        resolvedConf("leaf1", [])
        resolvedConf("leaf2", [])

        when:
        def result = builder.getResult()

        then:
        print(result.root) == """x:root:1
  x:mid1:1 [root]
    x:leaf1:1 [mid1]
    x:leaf2:1 [mid1]
"""
    }

    def "builds graph without unresolved deps"() {
        given:
        builder.start(confId("a"))
        resolvedConf("a", [dep("b"), dep("c"), dep("U", new RuntimeException("unresolved!"))])
        resolvedConf("b", [])
        resolvedConf("c", [])

        when:
        def result = builder.getResult()

        then:
        print(result.root) == """x:a:1
  x:b:1 [a]
  x:c:1 [a]
"""
    }

    private void resolvedConf(String module, List<InternalDependencyResult> deps) {
        builder.resolvedConfiguration(confId(module), deps)
    }

    private InternalDependencyResult dep(String requested, Exception failure = null, String selected = requested, ModuleVersionSelectionReason selectionReason = VersionSelectionReasons.REQUESTED) {
        def selection = failure != null ? null : new DummyModuleVersionSelection(selectedId: newId("x", selected, "1"), selectionReason: selectionReason)
        new DummyInternalDependencyResult(requested: newSelector("x", requested, "1"), selected: selection, failure: failure)
    }

    private ModuleVersionIdentifier confId(String module) {
        newId("x", module, "1")
    }

    String print(ResolvedModuleVersionResult root) {
        StringBuilder sb = new StringBuilder();
        sb.append(root).append("\n");
        for (ResolvedDependencyResult d : root.getDependencies()) {
            print(d, sb, new HashSet(), "  ");
        }

        sb.toString();
    }

    void print(ResolvedDependencyResult dep, StringBuilder sb, Set visited, String indent) {
        if (!visited.add(dep.getSelected())) {
            return;
        }
        sb.append(indent + dep + " [" + dep.selected.dependents*.from.id.name.join(",") + "]\n");
        for (ResolvedDependencyResult d : dep.getSelected().getDependencies()) {
            print(d, sb, visited, "  " + indent);
        }
    }

    class DummyModuleVersionSelection implements ModuleVersionSelection{
        ModuleVersionIdentifier selectedId
        ModuleVersionSelectionReason selectionReason
    }

    class DummyInternalDependencyResult implements InternalDependencyResult {
        ModuleVersionSelector requested
        ModuleVersionSelection selected
        Exception failure
    }
}
