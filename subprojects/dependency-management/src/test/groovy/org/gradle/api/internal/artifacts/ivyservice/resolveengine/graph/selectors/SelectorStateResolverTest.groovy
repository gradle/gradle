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

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleSelectors
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveOptimizations
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.FIXED_10
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.FIXED_9
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_10_11
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_14_16
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.RANGE_7_8
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_DEPENDENCY_WITH_REJECT
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_EMPTY
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_FOUR_DEPENDENCIES
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_PREFER_BATCH1
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_PREFER_BATCH2
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_SINGLE
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_THREE_DEPENDENCIES
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_TWO_DEPENDENCIES_BATCH1
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_TWO_DEPENDENCIES_BATCH2
import static org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios.SCENARIOS_WITH_REJECT

/**
 * Unit test coverage of dependency resolution of a single module version, given a set of input selectors.
 */
class SelectorStateResolverTest extends Specification {
    private final TestComponentResolutionState root = new TestComponentResolutionState(DefaultModuleVersionIdentifier.newId("other", "root", "1"))
    private final componentIdResolver = new TestDependencyToComponentIdResolver()
    private final DefaultVersionComparator versionComparator = new DefaultVersionComparator()
    private final conflictResolver = new ConflictResolverFactory(versionComparator, new VersionParser()).createConflictResolver(ConflictResolution.latest)
    private final componentFactory = new TestComponentFactory()
    private final ModuleIdentifier moduleId = DefaultModuleIdentifier.newId("org", "module")
    private final ResolveOptimizations resolveOptimizations = new ResolveOptimizations()
    private final VersionParser versionParser = new VersionParser()
    private final SelectorStateResolver conflictHandlingResolver = new SelectorStateResolver(conflictResolver, componentFactory, root, resolveOptimizations, versionComparator.asVersionComparator(), versionParser)
    private final SelectorStateResolver failingResolver = new SelectorStateResolver(new FailingConflictResolver(), componentFactory, root, resolveOptimizations, versionComparator.asVersionComparator(), versionParser)

    def "resolve selector #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_SINGLE
    }

    def "resolve pair #permutation (batch 1)"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_TWO_DEPENDENCIES_BATCH1
    }

    def "resolve pair #permutation (batch 2)"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_TWO_DEPENDENCIES_BATCH2
    }

    def "resolve empty pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_EMPTY
    }

    def "resolve prefer pair #permutation (batch 1)"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_PREFER_BATCH1
    }

    def "resolve prefer pair #permutation (batch 2)"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_PREFER_BATCH2
    }

    def "resolve reject pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_DEPENDENCY_WITH_REJECT
    }

    def "resolve three #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(permutation.conflicts).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_THREE_DEPENDENCIES
    }

    def "resolve deps with reject #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(true).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_WITH_REJECT
    }

    def "resolve four #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expectedSingle

        expect:
        resolver(true).resolve(candidates) == expected

        where:
        permutation << SCENARIOS_FOUR_DEPENDENCIES
    }

    def 'short circuits for matching project selectors'() {
        def projectId = new DefaultProjectComponentIdentifier(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "projectA")
        def nine = new TestProjectSelectorState(projectId)
        def otherNine = new TestProjectSelectorState(projectId)
        ModuleConflictResolver mockResolver = Mock()
        SelectorStateResolver resolverWithMock = new SelectorStateResolver(mockResolver, componentFactory, root, resolveOptimizations, versionComparator.asVersionComparator(), versionParser)

        when:
        def selected = resolverWithMock.selectBest(moduleId, moduleSelectors([nine, otherNine]))

        then:
        selected.componentId == projectId
        selected.version == TestProjectSelectorState.VERSION
        0 * mockResolver._
    }

    def "performs partial resolve when some selectors fail"() {
        def missingLow = new TestModuleSelectorState(componentIdResolver, RANGE_7_8.versionConstraint)
        def nine = new TestModuleSelectorState(componentIdResolver, FIXED_9.versionConstraint)
        def ten = new TestModuleSelectorState(componentIdResolver, FIXED_10.versionConstraint)
        def range = new TestModuleSelectorState(componentIdResolver, RANGE_10_11.versionConstraint)
        def missingHigh = new TestModuleSelectorState(componentIdResolver, RANGE_14_16.versionConstraint)

        when:
        def selected = conflictHandlingResolver.selectBest(moduleId, moduleSelectors([missingLow, nine, ten, range, missingHigh]))

        then:
        selected.version == "10"
        missingLow.requireResult.failure instanceof ModuleVersionNotFoundException
         missingHigh.requireResult.failure instanceof ModuleVersionNotFoundException
    }

    def "rethrows failure when all selectors fail to resolve"() {
        def missingLow = new TestModuleSelectorState(componentIdResolver, RANGE_7_8.versionConstraint)
        def missingHigh = new TestModuleSelectorState(componentIdResolver, RANGE_14_16.versionConstraint)
        def valid = new TestModuleSelectorState(componentIdResolver, FIXED_10.versionConstraint)

        when:
        conflictHandlingResolver.selectBest(moduleId, moduleSelectors([missingLow]))

        then:
        thrown(ModuleVersionResolveException)

        when:
        conflictHandlingResolver.selectBest(moduleId, moduleSelectors([missingLow, missingHigh]))

        then:
        thrown(ModuleVersionResolveException)

        when:
        conflictHandlingResolver.selectBest(moduleId, moduleSelectors([missingLow, missingHigh, valid]))

        then:
        noExceptionThrown()
    }


    TestResolver resolver(boolean allowConflictResolution) {
        if (allowConflictResolution) {
            return new TestResolver(conflictHandlingResolver)
        }
        return new TestResolver(failingResolver)
    }

    ModuleSelectors moduleSelectors(List<? extends ResolvableSelectorState> selectors) {
        def moduleSelectors = new ModuleSelectors<ResolvableSelectorState>(versionComparator.asVersionComparator(), versionParser)
        selectors.forEach { moduleSelectors.add(it, false) }
        return moduleSelectors
    }

    class TestResolver {
        final SelectorStateResolver ssr

        TestResolver(SelectorStateResolver ssr) {
            this.ssr = ssr
        }

        String resolve(VersionRangeResolveTestScenarios.RenderableVersion... versions) {
            List<TestModuleSelectorState> selectors = versions.collect { version ->
                new TestModuleSelectorState(componentIdResolver, version.versionConstraint)
            }
            def currentSelection = ssr.selectBest(moduleId, moduleSelectors(selectors))
            if (selectors.any { it.requireResult?.failure != null || it.preferResult?.failure != null }) {
                return VersionRangeResolveTestScenarios.FAILED
            }
            if (currentSelection.isRejected()) {
                return VersionRangeResolveTestScenarios.REJECTED
            }
            return currentSelection.getVersion()
        }
    }

    static class TestComponentFactory implements ComponentStateFactory<ComponentResolutionState> {
        @Override
        ComponentResolutionState getRevision(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier id, ComponentGraphResolveState state) {
            return new TestComponentResolutionState(componentIdentifier, id)
        }
    }

    static class FailingConflictResolver<T> implements ModuleConflictResolver<T> {
        @Override
        void select(ConflictResolverDetails<T> details) {
            assert false : "Unexpected conflict resolution: " + details.candidates.collect {it.id}
        }
    }

    /**
     * A resolver used for testing the provides a fixed range of component identifiers.
     * The requested group/module is ignored, and the versions [9..13] are served.
     */
    class TestDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
        @Override
        void resolve(DependencyMetadata dependency, VersionSelector acceptor, VersionSelector rejector, BuildableComponentIdResolveResult result) {
            if (!acceptor.isDynamic()) {
                def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(moduleId.group, moduleId.name), acceptor.selector)
                resolvedOrRejected(id, rejector, result)
                return
            }

            def resolved = findDynamicVersion(acceptor, rejector)
            if (resolved) {
                def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(moduleId.group, moduleId.name), resolved as String)
                resolvedOrRejected(id, rejector, result)
                return
            }

            result.failed(missing(acceptor))
        }

        def resolvedOrRejected(ModuleComponentIdentifier id, VersionSelector rejectSelector, BuildableComponentIdResolveResult result) {
            if (rejectSelector != null && rejectSelector.accept(id.version)) {
                result.rejected(id, DefaultModuleVersionIdentifier.newId(id))
            } else {
                result.resolved(id, DefaultModuleVersionIdentifier.newId(id))
            }
        }

        private Integer findDynamicVersion(VersionSelector prefer, VersionSelector reject) {
            def resolved = (13..9).find {
                String candidateVersion = it as String
                ComponentMetadata candidate = new DummyComponentMetadata(candidateVersion)
                prefer.accept(candidate) && !rejected(reject, candidateVersion)
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
            def moduleComponentSelector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(moduleId.group, moduleId.name), prefer.selector)
            return new ModuleVersionNotFoundException(moduleComponentSelector, [])
        }
    }

    private static class DummyComponentMetadata implements ComponentMetadata {
        private final String version

        DummyComponentMetadata(String version) {
            this.version = version
        }

        @Override
        ModuleVersionIdentifier getId() {
            return DefaultModuleVersionIdentifier.newId("org", "foo", version)
        }

        @Override
        boolean isChanging() {
            return false
        }

        @Override
        String getStatus() {
            return "integration"
        }

        @Override
        List<String> getStatusScheme() {
            return ["integration"]
        }

        @Override
        AttributeContainer getAttributes() {
            return ImmutableAttributes.EMPTY
        }
    }

}
