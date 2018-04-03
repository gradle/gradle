/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.FIXED_10
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.FIXED_9
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_10_11
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_14_16
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_7_8
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_DEPENDENCY_WITH_REJECT
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_FOUR_DEPENDENCIES
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_THREE_DEPENDENCIES
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_TWO_DEPENDENCIES
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_WITH_REJECT

/**
 * Unit test coverage of dependency resolution of a single module version, given a set of input selectors.
 */
class SelectorStateResolverTest extends Specification {
    private final TestComponentResolutionState root = new TestComponentResolutionState(DefaultModuleVersionIdentifier.newId("other", "root", "1"))
    private final componentIdResolver = new TestDependencyToComponentIdResolver()
    private final conflictResolver = new TestModuleConflictResolver()
    private final componentFactory = new TestComponentFactory()
    private final ModuleIdentifier moduleId = DefaultModuleIdentifier.newId("org", "module")

    private final SelectorStateResolver selectorStateResolver = new SelectorStateResolver(conflictResolver, componentFactory, root)

    @Unroll
    def "resolve pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        resolve(candidates) == expected

        where:
        permutation << SCENARIOS_TWO_DEPENDENCIES
    }

    @Unroll
    def "resolve reject pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        resolve(candidates) == expected

        where:
        permutation << SCENARIOS_DEPENDENCY_WITH_REJECT
    }

    @Unroll
    def "resolve three #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        resolve(candidates) == expected

        where:
        permutation << SCENARIOS_THREE_DEPENDENCIES
    }

    @Unroll
    def "resolve deps with reject #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        resolve(candidates) == expected

        where:
        permutation << SCENARIOS_WITH_REJECT
    }

    @Unroll
    def "resolve four #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        resolve(candidates) == expected

        where:
        permutation << SCENARIOS_FOUR_DEPENDENCIES
    }

    def "performs partial resolve when some selectors fail"() {
        def missingLow = new TestSelectorState(componentIdResolver, RANGE_7_8.versionConstraint)
        def nine = new TestSelectorState(componentIdResolver, FIXED_9.versionConstraint)
        def ten = new TestSelectorState(componentIdResolver, FIXED_10.versionConstraint)
        def range = new TestSelectorState(componentIdResolver, RANGE_10_11.versionConstraint)
        def missingHigh = new TestSelectorState(componentIdResolver, RANGE_14_16.versionConstraint)

        when:
        def selected = selectorStateResolver.selectBest(moduleId, [missingLow, nine, ten, range, missingHigh])

        then:
        selected.version == "10"
        missingLow.resolved.failure instanceof ModuleVersionNotFoundException
        missingLow.resolved.failure instanceof ModuleVersionNotFoundException
        missingHigh.resolved.failure instanceof ModuleVersionNotFoundException
    }

    def "rethrows failure when all selectors fail to resolve"() {
        def missingLow = new TestSelectorState(componentIdResolver, RANGE_7_8.versionConstraint)
        def missingHigh = new TestSelectorState(componentIdResolver, RANGE_14_16.versionConstraint)
        def valid = new TestSelectorState(componentIdResolver, FIXED_10.versionConstraint)

        when:
        selectorStateResolver.selectBest(moduleId, [missingLow])

        then:
        thrown(ModuleVersionResolveException)

        when:
        selectorStateResolver.selectBest(moduleId, [missingLow, missingHigh])

        then:
        thrown(ModuleVersionResolveException)

        when:
        selectorStateResolver.selectBest(moduleId, [missingLow, missingHigh, valid])

        then:
        noExceptionThrown()
    }

    String resolve(VersionRangeResolveTestScenarios.RenderableVersion... versions) {
        List<TestSelectorState> selectors = versions.collect { version ->
            new TestSelectorState(componentIdResolver, version.versionConstraint)
        }
        def currentSelection = selectorStateResolver.selectBest(moduleId, selectors)
        if (currentSelection.isRejected()) {
            return VersionRangeResolveTestScenarios.REJECTED
        }
        if (selectors.any { it.resolved?.failure != null }) {
            return VersionRangeResolveTestScenarios.FAILED
        }
        return currentSelection.getVersion()
    }

    static class TestComponentFactory implements ComponentStateFactory<ComponentResolutionState> {
        @Override
        ComponentResolutionState getRevision(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier id, ComponentResolveMetadata metadata) {
            return new TestComponentResolutionState(id)
        }
    }

    static class TestModuleConflictResolver implements ModuleConflictResolver {
        @Override
        <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
            Comparator<T> versionComparator = new ComponentVersionComparator() as Comparator<T>
            T max = details.candidates.max(versionComparator)
            details.select(max)
        }

        private static class ComponentVersionComparator implements Comparator<ComponentResolutionState> {
            private final Comparator<Version> versionComparator = new DefaultVersionComparator().asVersionComparator()
            private final VersionParser versionParser = VersionParser.INSTANCE

            @Override
            int compare(ComponentResolutionState one, ComponentResolutionState two) {
                Version v1 = versionParser.transform(one.version)
                Version v2 = versionParser.transform(two.version)
                return versionComparator.compare(v1, v2)
            }
        }
    }
    /**
     * A resolver used for testing the provides a fixed range of component identifiers.
     * The requested group/module is ignored, and the versions [9..13] are served.
     */
    class TestDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
        @Override
        void resolve(DependencyMetadata dependency, ResolvedVersionConstraint versionConstraint, BuildableComponentIdResolveResult result) {
            def prefer = versionConstraint.preferredSelector
            def reject = versionConstraint.rejectedSelector

            if (!prefer.isDynamic()) {
                def id = DefaultModuleComponentIdentifier.newId(moduleId.group, moduleId.name, prefer.selector)
                result.resolved(id, DefaultModuleVersionIdentifier.newId(id))
                return
            }

            def resolved = findDynamicVersion(prefer, reject)
            if (resolved) {
                def id = DefaultModuleComponentIdentifier.newId(moduleId.group, moduleId.name, resolved as String)
                result.resolved(id, DefaultModuleVersionIdentifier.newId(id))
                return
            }

            result.failed(missing(prefer))
        }

        private Integer findDynamicVersion(VersionSelector prefer, VersionSelector reject) {
            def resolved = (13..9).find {
                String candidateVersion = it as String
                prefer.accept(candidateVersion) && !rejected(reject, candidateVersion)
            }
            if (!resolved) {
                resolved = (13..9).find {
                    String candidateVersion = it as String
                    prefer.accept(candidateVersion)
                }
            }
            resolved
        }

        private boolean rejected(VersionSelector reject, String version) {
            return reject != null && reject.accept(version)
        }

        private ModuleVersionNotFoundException missing(VersionSelector prefer) {
            def moduleComponentSelector = DefaultModuleComponentSelector.newSelector(moduleId.group, moduleId.name, prefer.selector)
            return new ModuleVersionNotFoundException(moduleComponentSelector, [])
        }
    }

}
