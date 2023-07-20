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

package org.gradle.api.tasks.diagnostics.internal.insight


import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyReportHeader
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.CONFLICT_RESOLUTION
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.FORCED
import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class DependencyInsightReporterSpec extends Specification {
    def versionParser = new VersionParser()
    def versionComparator = new DefaultVersionComparator()
    def versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, versionParser)

    @Subject
    def reporter = new DependencyInsightReporter(versionSelectorScheme, versionComparator, versionParser)

    def "sorts dependencies"() {
        def dependencies = [dep("a", "x", "1.0", "2.0"), dep("a", "x", "1.5", "2.0"), dep("b", "a", "5.0"), dep("a", "z", "1.0"), dep("a", "x", "2.0")]

        when:
        def sorted = reporter.convertToRenderableItems(dependencies, false)

        then:
        sorted.size() == 8

        sorted[0].name == 'a:x:2.0'
        !sorted[0].description

        sorted[1].name == 'a:x:2.0'
        !sorted[1].description

        sorted[2].name == 'a:x:1.0 -> 2.0'
        !sorted[2].description

        sorted[3].name == 'a:x:1.5 -> 2.0'
        !sorted[3].description

        sorted[4].name == 'a:z:1.0'
        !sorted[4].description

        sorted[5].name == 'a:z:1.0'
        !sorted[5].description

        sorted[6].name == 'b:a:5.0'
        !sorted[6].description

        sorted[7].name == 'b:a:5.0'
        !sorted[7].description
    }

    def "adds header dependency if the selected version does not exist in the graph"() {
        def dependencies = [dep("a", "x", "1.0", "2.0", forced()), dep("a", "x", "1.5", "2.0", forced()), dep("b", "a", "5.0")]

        when:
        def sorted = reporter.convertToRenderableItems(dependencies, false)

        then:
        sorted.size() == 5

        sorted[0].name == 'a:x:2.0'
        sorted[0].description == 'forced'

        sorted[1].name == 'a:x:1.0 -> 2.0'
        !sorted[1].description

        sorted[2].name == 'a:x:1.5 -> 2.0'
        !sorted[2].description

        sorted[3].name == 'b:a:5.0'
        !sorted[3].description

        sorted[4].name == 'b:a:5.0'
        !sorted[4].description
    }

    def "annotates only first dependency in the group"() {
        def dependencies = [dep("a", "x", "1.0", "2.0", conflict()), dep("a", "x", "2.0", "2.0", conflict()), dep("b", "a", "5.0", "5.0", forced())]

        when:
        def sorted = reporter.convertToRenderableItems(dependencies, false)

        then:
        sorted.size() == 5

        sorted[0].name == 'a:x:2.0'
        sorted[0].description == 'by conflict resolution'

        sorted[1].name == 'a:x:2.0'
        sorted[1].description == null

        sorted[2].name == 'a:x:1.0 -> 2.0'
        !sorted[2].description

        sorted[3].name == 'b:a:5.0'
        sorted[3].description == 'forced'

        sorted[4].name == 'b:a:5.0'
        sorted[4].description == null
    }

    def "can limit to a single path to a dependency"() {
        def dependencies = [
            path('a:1.0 -> b:1.0 -> c:1.0'),
            path('a:1.0 -> d:1.0'),
            path('a:1.0 -> e:1.0 -> f:1.0')
        ]

        when:
        def sorted = reporter.convertToRenderableItems(dependencies, false)

        then:
        sorted.size() == 2
        verify(sorted[0]) {
            selected 'group:a:1.0'
            isHeader()
            noMoreChildren()
        }
        verify(sorted[1]) {
            selected 'group:a:1.0'
            isNotHeader()
            hasChild('group:b:1.0') {
                noMoreChildren()
            }
            hasChild('group:d:1.0') {
                noMoreChildren()
            }
            hasChild('group:e:1.0') {
                noMoreChildren()
            }
            noMoreChildren()
        }

        when:
        sorted = reporter.convertToRenderableItems(dependencies, true)

        then:
        sorted.size() == 2
        verify(sorted[0]) {
            selected 'group:a:1.0'
            isHeader()
            noMoreChildren()
        }
        verify(sorted[1]) {
            selected 'group:a:1.0'
            isNotHeader()
            hasChild('group:b:1.0') {
                noMoreChildren()
            }
            // siblings are ignored
            noMoreChildren()
        }
    }

    private static void verify(RenderableDependency result, @DelegatesTo(value = RenderableDependencyResultFixture, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        spec.delegate = new RenderableDependencyResultFixture(result)
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private static DefaultResolvedDependencyResult dep(String group, String name, String requested, String selected = requested, ComponentSelectionReason selectionReason = ComponentSelectionReasons.requested()) {
        def selectedModule = new DefaultResolvedComponentResult(newId(group, name, selected), selectionReason, new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), selected), [1: defaultVariant()], [defaultVariant()], "repoId")
        new DefaultResolvedDependencyResult(newSelector(DefaultModuleIdentifier.newId(group, name), requested),
                false,
                selectedModule,
                null,
                new DefaultResolvedComponentResult(newId("a", "root", "1"), ComponentSelectionReasons.requested(), new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), selected), [1: defaultVariant()], [defaultVariant()], "repoId"))
    }

    private static DefaultResolvedVariantResult defaultVariant(String ownerGroup = 'com', String ownerModule = 'foo', String ownerVersion = '1.0') {
        def ownerId = DefaultModuleComponentIdentifier.newId(
            DefaultModuleVersionIdentifier.newId(ownerGroup, ownerModule, ownerVersion)
        )
        new DefaultResolvedVariantResult(ownerId, Describables.of("default"), ImmutableAttributes.EMPTY, [], null)
    }

    private static DefaultResolvedDependencyResult path(String path) {
        DefaultResolvedComponentResult from = new DefaultResolvedComponentResult(newId("group", "root", "1"), ComponentSelectionReasons.requested(), new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId("group", "root"), "1"), [1: defaultVariant()], [defaultVariant()], "repoId")
        List<DefaultResolvedDependencyResult> pathElements = (path.split(' -> ') as List).reverse().collect {
            def (name, version) = it.split(':')
            def componentResult = new DefaultResolvedComponentResult(newId('group', name, version), ComponentSelectionReasons.requested(), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('group', name), version), [1: defaultVariant()], [defaultVariant()], "repoId")
            def result = new DefaultResolvedDependencyResult(newSelector(DefaultModuleIdentifier.newId("group", name), version), false, componentResult, null, from)
            from = componentResult
            result
        }
        return pathElements[-1]
    }

    private static ComponentSelectionReason forced() {
        ComponentSelectionReasons.of(FORCED)
    }

    private static ComponentSelectionReason conflict() {
        ComponentSelectionReasons.of(CONFLICT_RESOLUTION)
    }

    private static class RenderableDependencyResultFixture {
        private final RenderableDependency actual
        private final Set<RenderableDependency> checkedChildren = []

        RenderableDependencyResultFixture(RenderableDependency result) {
            this.actual = result
        }

        void selected(String name) {
            assert actual.name == name
        }

        void isHeader() {
            assert actual instanceof DependencyReportHeader
        }

        void isNotHeader() {
            assert !(actual instanceof DependencyReportHeader)
        }

        void hasChild(String name, Closure<?> spec) {
            def child = actual.children.find { it.name == name }
            assert child != null: "Unable to find child named $name. Known children to ${actual.name} = ${actual.children.name}"
            checkedChildren << child
            verify(child, spec)
        }

        void noMoreChildren() {
            assert checkedChildren.size() == actual.children.size()
        }
    }
}
