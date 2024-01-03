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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvedDependencyEdge
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newDependency
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newModule
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newUnresolvedDependency
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newVariant

class DefaultResolutionResultTest extends Specification {

    def "provides all modules and dependencies including unresolved"() {
        given:
        def dep1 = newDependency('dep1')
        def dep2 = newDependency('dep2')

        def root = newModule('root').addDependency(dep1).addDependency(dep2)

        def dep3 = newDependency('dep3')
        def dep4 = newUnresolvedDependency('dep4')

        dep2.selected.addDependency(dep3).addDependency(dep4)

        when:
        def deps = newResolutionResult(root).allDependencies
        def modules = newResolutionResult(root).allComponents

        then:
        deps == [dep1, dep2, dep3, dep4] as Set

        and:
        //does not contain unresolved dep, contains root
        modules == [root, dep1.selected, dep2.selected, dep3.selected] as Set
    }

    def "provides hooks for iterating each module or dependency exactly once"() {
        given:
        //root -> dep1,dep2; dep1 -> dep3
        def dep = newDependency('dep1')
        def dep3 = newDependency('dep3')
        def root = newModule('root').addDependency(dep).addDependency(newDependency('dep2')).addDependency(dep3)
        dep.selected.addDependency(dep3)

        def result = newResolutionResult(root)

        when:
        def deps = []
        def modules = []
        result.allDependencies { deps << it }
        result.allComponents { modules << it }

        then:
        deps*.requested.group == ['dep1', 'dep3', 'dep2', 'dep3']

        and:
        modules*.id.group == ['root', 'dep1', 'dep3', 'dep2']
    }

    def "deals with dependency cycles"() {
        given:
        // a->b->a
        def root = newModule('a', 'a', '1')
        def dep1 = newDependency('b', 'b', '1')
        root.addDependency(dep1)
        dep1.selected.addDependency(new DefaultResolvedDependencyResult(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId('a', 'a'), '1'), false, root, newVariant(), dep1.selected))

        when:
        def deps = newResolutionResult(root).allDependencies
        def modules = newResolutionResult(root).allComponents

        then:
        deps.size() == 2
        modules.size() == 2
    }

    def "mutating all dependencies or modules is harmless"() {
        given:
        def dep1 = newDependency('dep1')
        def dep2 = newDependency('dep2')

        def root = newModule('root').addDependency(dep1).addDependency(dep2)

        when:
        def result = newResolutionResult(root)

        then:
        result.allDependencies == [dep1, dep2] as Set
        result.allComponents == [root, dep1.selected, dep2.selected] as Set

        when:
        result.allDependencies << newDependency('dep3')
        result.allComponents << newModule('foo')

        then:
        result.allDependencies == [dep1, dep2] as Set
        result.allComponents == [root, dep1.selected, dep2.selected] as Set
    }

    def "doesn't throw class cast exception when the source of the edge is a project"() {
        def projectId = new DefaultProjectComponentIdentifier(
            Stub(BuildIdentifier),
            Stub(Path),
            Stub(Path),
            'test project'
        )
        def mid = DefaultModuleVersionIdentifier.newId("foo", "bar", "1.0")
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def dep = new DefaultUnresolvedDependencyResult(
            Stub(ComponentSelector), false,
            Stub(ComponentSelectionReason),
            new DefaultResolvedComponentResult(mid, Stub(ComponentSelectionReason), projectId, [1: Stub(ResolvedVariantResult)], [Stub(ResolvedVariantResult)], null),
            new ModuleVersionNotFoundException(Stub(ModuleComponentSelector), broken, [])
        )
        def edge = new UnresolvedDependencyEdge(dep)

        when:
        def from = edge.from

        then:
        from.is(projectId)
    }

    private static ResolutionResult newResolutionResult(root) {
        new DefaultResolutionResult(new DefaultMinimalResolutionResult(() -> root, ImmutableAttributes.EMPTY))
    }

}
