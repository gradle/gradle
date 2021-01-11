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
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultResolverResults
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
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
    def configuration = Stub(ConfigurationInternal)
    def dependencies = Stub(DependencySet)
    def dependencyConstraints = Stub(DependencyConstraintSet)
    def componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    def results = new DefaultResolverResults()
    def moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()

    def dependencyResolver = new ShortCircuitEmptyConfigurationResolver(delegate, componentIdentifierFactory, moduleIdentifierFactory, Stub(BuildIdentifier))

    def "returns empty build dependencies when no dependencies"() {
        def depVisitor = Mock(TaskDependencyResolveContext)
        def artifactVisitor = Mock(ArtifactVisitor)

        given:
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveBuildDependencies(configuration, results)

        then:
        def localComponentsResult = results.resolvedLocalComponents
        localComponentsResult.resolvedProjectConfigurations as List == []

        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Specs.satisfyAll(), null, Specs.satisfyAll(), true)
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
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveGraph(configuration, results)

        then:
        def result = results.resolutionResult
        result.allComponents.size() == 1
        result.allDependencies.empty

        and:
        def localComponentsResult = results.resolvedLocalComponents
        localComponentsResult.resolvedProjectConfigurations as List == []

        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Specs.satisfyAll(), null, Specs.satisfyAll(), true)
        artifactSet.visitDependencies(depVisitor)
        artifactSet.visitArtifacts(artifactVisitor, true)

        and:
        0 * depVisitor._
        0 * artifactVisitor._
        0 * delegate._
    }

    def "returns empty result when no dependencies"() {
        given:
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveGraph(configuration, results)
        dependencyResolver.resolveArtifacts(configuration, results)

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

        configuration.name >> 'lockedConf'
        configuration.resolutionStrategy >> resolutionStrategy
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveBuildDependencies(configuration, results)

        then:

        0 * resolutionStrategy._
    }

    def 'empty graph result still interacts with dependency locking'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()
        DependencyLockingProvider lockingProvider = Mock()
        DependencyLockingState lockingState = Mock()

        configuration.name >> 'lockedConf'
        configuration.resolutionStrategy >> resolutionStrategy
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveGraph(configuration, results)

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

        configuration.name >> 'lockedConf'
        configuration.resolutionStrategy >> resolutionStrategy
        dependencies.isEmpty() >> true
        dependencyConstraints.isEmpty() >> true
        configuration.getAllDependencies() >> dependencies
        configuration.getAllDependencyConstraints() >> dependencyConstraints

        when:
        dependencyResolver.resolveGraph(configuration, results)

        then:
        1 * resolutionStrategy.dependencyLockingEnabled >> true
        1 * resolutionStrategy.dependencyLockingProvider >> lockingProvider
        1 * lockingProvider.loadLockState('lockedConf') >> lockingState
        1 * lockingState.mustValidateLockState() >> true
        1 * lockingState.lockedDependencies >> [DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('org', 'foo'), '1.0')]
        1 * delegate.resolveGraph(configuration, results)
    }

    def "delegates to backing service to resolve build dependencies when there are one or more dependencies"() {
        given:
        dependencies.isEmpty() >> false
        configuration.getAllDependencies() >> dependencies

        when:
        dependencyResolver.resolveBuildDependencies(configuration, results)

        then:
        1 * delegate.resolveBuildDependencies(configuration, results)
    }

    def "delegates to backing service to resolve graph when there are one or more dependencies"() {
        given:
        dependencies.isEmpty() >> false
        configuration.getAllDependencies() >> dependencies

        when:
        dependencyResolver.resolveGraph(configuration, results)

        then:
        1 * delegate.resolveGraph(configuration, results)
    }

    def "delegates to backing service to resolve artifacts when there are one or more dependencies"() {
        given:
        dependencies.isEmpty() >> false
        configuration.getAllDependencies() >> dependencies

        when:
        dependencyResolver.resolveArtifacts(configuration, results)

        then:
        1 * delegate.resolveArtifacts(configuration, results)
    }
}
