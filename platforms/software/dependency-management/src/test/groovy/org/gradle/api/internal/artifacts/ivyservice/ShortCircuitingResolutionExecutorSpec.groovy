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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ResolveContext
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.model.CalculatedValue
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

class ShortCircuitingResolutionExecutorSpec extends Specification {

    def delegate = Mock(ResolutionExecutor)

    def dependencyResolver = new ShortCircuitingResolutionExecutor(delegate, new AttributeDesugaring(AttributeTestUtil.attributesFactory()))

    def "returns empty build dependencies when no dependencies"() {
        def depVisitor = Mock(TaskDependencyResolveContext)
        def artifactVisitor = Mock(ArtifactVisitor)

        given:
        ResolveContext resolveContext = confWithoutDependencies()

        when:
        def results = dependencyResolver.resolveBuildDependencies(resolveContext, Stub(CalculatedValue))

        then:
        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Mock(ArtifactSelectionSpec))
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
        ResolveContext resolveContext = confWithoutDependencies()

        when:
        def results = dependencyResolver.resolveGraph(resolveContext, [])

        then:
        results.visitedGraph.resolutionResult.rootSource.get().dependencies.empty

        def visitedArtifacts = results.visitedArtifacts
        def artifactSet = visitedArtifacts.select(Mock(ArtifactSelectionSpec))
        artifactSet.visitDependencies(depVisitor)
        artifactSet.visitArtifacts(artifactVisitor, true)

        and:
        0 * depVisitor._
        0 * artifactVisitor._
        0 * delegate._
    }

    def "returns empty result when no dependencies"() {
        given:
        ResolveContext resolveContext = confWithoutDependencies()

        when:
        def results = dependencyResolver.resolveGraph(resolveContext, [])

        then:
        def resolvedConfig = results.legacyResults.resolvedConfiguration
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

        ResolveContext resolveContext = confWithoutDependencies()
        resolveContext.name >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy

        when:
        dependencyResolver.resolveBuildDependencies(resolveContext, Stub(CalculatedValue))

        then:

        0 * resolutionStrategy._
    }

    def 'empty graph result still interacts with dependency locking'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()
        DependencyLockingProvider lockingProvider = Mock()
        DependencyLockingState lockingState = Mock()

        ResolveContext resolveContext = confWithoutDependencies()
        resolveContext.dependencyLockingId >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy

        when:
        dependencyResolver.resolveGraph(resolveContext, [])

        then:

        1 * resolutionStrategy.dependencyLockingEnabled >> true
        1 * resolutionStrategy.dependencyLockingProvider >> lockingProvider
        1 * lockingProvider.loadLockState('lockedConf', _) >> lockingState
        1 * lockingState.mustValidateLockState() >> false
        1 * lockingProvider.persistResolvedDependencies('lockedConf', _, Collections.emptySet(), Collections.emptySet())
    }

    def 'empty result with non empty lock state causes resolution through delegate'() {
        given:
        ResolutionStrategyInternal resolutionStrategy = Mock()
        DependencyLockingProvider lockingProvider = Mock()
        DependencyLockingState lockingState = Mock()
        ResolverResults delegateResults = Mock()
        List<ResolutionAwareRepository> repos = Mock()

        ResolveContext resolveContext = confWithoutDependencies()
        resolveContext.dependencyLockingId >> 'lockedConf'
        resolveContext.resolutionStrategy >> resolutionStrategy

        when:
        def results = dependencyResolver.resolveGraph(resolveContext, repos)

        then:
        1 * resolutionStrategy.dependencyLockingEnabled >> true
        1 * resolutionStrategy.dependencyLockingProvider >> lockingProvider
        1 * lockingProvider.loadLockState('lockedConf', _) >> lockingState
        1 * lockingState.mustValidateLockState() >> true
        1 * lockingState.lockedDependencies >> [DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('org', 'foo'), '1.0')]
        1 * delegate.resolveGraph(resolveContext, repos) >> delegateResults
        results == delegateResults
    }

    def "delegates to backing service to resolve build dependencies when there are one or more dependencies"() {
        given:
        ResolverResults delegateResults = Mock()
        ResolveContext resolveContext = confWithDependencies()

        when:
        def results = dependencyResolver.resolveBuildDependencies(resolveContext, Stub(CalculatedValue))

        then:
        1 * delegate.resolveBuildDependencies(resolveContext, _) >> delegateResults
        results == delegateResults
    }

    def "delegates to backing service to resolve graph when there are one or more dependencies"() {
        given:
        ResolverResults delegateResults = Mock()
        ResolveContext resolveContext = confWithDependencies()
        List<ResolutionAwareRepository> repos = Mock()

        when:
        def results = dependencyResolver.resolveGraph(resolveContext, repos)

        then:
        1 * delegate.resolveGraph(resolveContext, repos) >> delegateResults
        results == delegateResults
    }

    ResolveContext confWithoutDependencies() {
        Stub(ResolveContext) {
            toRootComponent() >> Stub(RootComponentMetadataBuilder.RootComponentState) {
                getRootVariant() >> Stub(LocalVariantGraphResolveState) {
                    getDependencies() >> Collections.emptyList()
                }
            }
        }
    }

    ResolveContext confWithDependencies() {
        Stub(ResolveContext) {
            toRootComponent() >> Stub(RootComponentMetadataBuilder.RootComponentState) {
                getRootVariant() >> Stub(LocalVariantGraphResolveState) {
                    getDependencies() >> [Mock(DependencyMetadata)]
                }
            }
        }
    }
}
