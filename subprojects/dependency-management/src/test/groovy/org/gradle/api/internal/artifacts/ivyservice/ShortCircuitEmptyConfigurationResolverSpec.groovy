/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.ResolveContext
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class ShortCircuitEmptyConfigurationResolverSpec extends Specification {

    def delegate = Mock(ConfigurationResolver)
    def resolveContext = Stub(ResolveContext)
    def componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    def moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()

    def dependencyResolver = new ShortCircuitEmptyConfigurationResolver(delegate, componentIdentifierFactory, moduleIdentifierFactory, Stub(BuildIdentifier))

    def "returns empty build dependencies when no dependencies"() {
        def depVisitor = Mock(TaskDependencyResolveContext)
        def artifactVisitor = Mock(ArtifactVisitor)

        given:
        resolveContext.hasDependencies() >> false

        when:
        def results = dependencyResolver.resolveBuildDependencies(resolveContext)

        then:
        def localComponentsResult = results.resolvedLocalComponents
        localComponentsResult.resolvedProjectConfigurations as List == []

        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Specs.satisfyAll(), null, Specs.satisfyAll(), true, false)
        artifactSet.visitDependencies(depVisitor)
        artifactSet.visitArtifacts(artifactVisitor, true)

        and:
        0 * depVisitor._
        0 * artifactVisitor._
        0 * delegate._
    }

    def "returns empty graph when no dependencies"() {
        def depVisitor = Mock(TaskDependencyResolveContext)
        def artifactVisitor = Mock(ArtifactVisitor)

        given:
        resolveContext.hasDependencies() >> false

        when:
        def results = dependencyResolver.resolveGraph(resolveContext)

        then:
        results.minimalResolutionResult.rootSource.get().dependencies.empty

        and:
        def localComponentsResult = results.resolvedLocalComponents
        localComponentsResult.resolvedProjectConfigurations as List == []

        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Specs.satisfyAll(), null, Specs.satisfyAll(), true, false)
        artifactSet.visitDependencies(depVisitor)
        artifactSet.visitArtifacts(artifactVisitor, true)

        and:
        0 * depVisitor._
        0 * artifactVisitor._
        0 * delegate._
    }

    def "returns empty result when no dependencies"() {
        given:
        resolveContext.hasDependencies() >> false

        when:
        def results = dependencyResolver.resolveGraph(resolveContext)
        results = dependencyResolver.resolveArtifacts(resolveContext, results)

        then:
        def resolvedConfig = results.resolvedConfiguration
        !resolvedConfig.hasError()
        resolvedConfig.rethrowFailure()

        resolvedConfig.getFiles(Specs.<Dependency> satisfyAll()).isEmpty()
        resolvedConfig.getFirstLevelModuleDependencies().isEmpty()
        resolvedConfig.getResolvedArtifacts().isEmpty()

        and:
        0 * delegate._
    }

    def 'empty graph result for build dependencies does not interact with dependency locking'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()

        resolveContext.name >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy
        resolveContext.hasDependencies() >> false

        when:
        dependencyResolver.resolveBuildDependencies(resolveContext)

        then:

        0 * resolutionStrategy._
    }

    def 'empty graph result still interacts with dependency locking'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()
        DependencyLockingProvider lockingProvider = Mock()
        DependencyLockingState lockingState = Mock()

        resolveContext.name >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy
        resolveContext.hasDependencies() >> false

        when:
        dependencyResolver.resolveGraph(resolveContext)

        then:

        1 * resolutionStrategy.dependencyLockingEnabled >> true
        1 * resolutionStrategy.dependencyLockingProvider >> lockingProvider
        1 * lockingProvider.loadLockState('lockedConf') >> lockingState
        1 * lockingState.mustValidateLockState() >> false
        1 * lockingProvider.persistResolvedDependencies('lockedConf', Collections.emptySet(), Collections.emptySet())
    }

    def 'empty result with non empty lock state causes resolution through delegate'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()
        DependencyLockingProvider lockingProvider = Mock()
        DependencyLockingState lockingState = Mock()
        ResolverResults delegateResults = Mock()

        resolveContext.name >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy
        resolveContext.hasDependencies() >> false

        when:
        def results = dependencyResolver.resolveGraph(resolveContext)

        then:
        1 * resolutionStrategy.dependencyLockingEnabled >> true
        1 * resolutionStrategy.dependencyLockingProvider >> lockingProvider
        1 * lockingProvider.loadLockState('lockedConf') >> lockingState
        1 * lockingState.mustValidateLockState() >> true
        1 * lockingState.lockedDependencies >> [DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('org', 'foo'), '1.0')]
        1 * delegate.resolveGraph(resolveContext) >> delegateResults
        results == delegateResults
    }

    def "delegates to backing service to resolve build dependencies when there are one or more dependencies"() {
        given:
        ResolverResults delegateResults = Mock()
        resolveContext.hasDependencies() >> true

        when:
        def results = dependencyResolver.resolveBuildDependencies(resolveContext)

        then:
        1 * delegate.resolveBuildDependencies(resolveContext) >> delegateResults
        results == delegateResults
    }

    def "delegates to backing service to resolve graph when there are one or more dependencies"() {
        given:
        ResolverResults delegateResults = Mock()
        resolveContext.hasDependencies() >> true

        when:
        def results = dependencyResolver.resolveGraph(resolveContext)

        then:
        1 * delegate.resolveGraph(resolveContext) >> delegateResults
        results == delegateResults
    }

    def "delegates to backing service to resolve artifacts when there are one or more dependencies"() {
        given:
        def graphResults = Mock(ResolverResults) {
            getArtifactResolveState() >> Mock(ArtifactResolveState)
        }
        ResolverResults delegateResults = Mock()
        resolveContext.hasDependencies() >> true

        when:
        def results = dependencyResolver.resolveArtifacts(resolveContext, graphResults)

        then:
        1 * delegate.resolveArtifacts(resolveContext, graphResults) >> delegateResults
        results == delegateResults
    }
}
