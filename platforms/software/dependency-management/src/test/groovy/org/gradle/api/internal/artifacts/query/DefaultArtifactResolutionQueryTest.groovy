/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.query

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry
import org.gradle.api.internal.component.ComponentTypeRegistration
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.cache.internal.DefaultInMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentArtifactResolveState
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.util.AttributeTestUtil
import org.gradle.util.internal.BuildCommencedTimeProvider
import spock.lang.Shared
import spock.lang.Specification

class DefaultArtifactResolutionQueryTest extends Specification {
    def configurationContainerInternal = Stub(ConfigurationContainerInternal)
    def resolveIvyFactory = Mock(ResolveIvyFactory)
    def globalDependencyResolutionRules = Mock(GlobalDependencyResolutionRules)
    def componentTypeRegistry = Mock(ComponentTypeRegistry)
    def artifactResolver = Mock(ArtifactResolver)
    def artifactTypeRegistry = Mock(ArtifactTypeRegistry)
    def repositoryChain = Mock(ComponentResolvers)
    def componentMetaDataResolver = Mock(ComponentMetaDataResolver)
    def ruleExecutor = new ComponentMetadataSupplierRuleExecutor(Stub(GlobalScopedCacheBuilderFactory), Stub(DefaultInMemoryCacheDecoratorFactory), Stub(ValueSnapshotter), Stub(BuildCommencedTimeProvider), Stub(Serializer))

    @Shared
    ComponentTypeRegistry testComponentTypeRegistry = createTestComponentTypeRegistry()

    def "cannot call withArtifacts multiple times"() {
        def query = createArtifactResolutionQuery(componentTypeRegistry)

        given:
        query.withArtifacts(Component, Artifact)

        when:
        query.withArtifacts(Component, Artifact)

        then:
        def e = thrown IllegalStateException
        e.message == "Cannot specify component type multiple times."
    }

    def "cannot call execute without first specifying arguments"() {
        def query = createArtifactResolutionQuery(componentTypeRegistry)

        when:
        query.execute()

        then:
        def e = thrown IllegalStateException
        e.message == "Must specify component type and artifacts to query."
    }

    def "invalid component type #selectedComponentType and artifact type #selectedArtifactType is wrapped in UnresolvedComponentResult"() {
        withArtifactResolutionInteractions()

        given:
        def query = createArtifactResolutionQuery(givenComponentTypeRegistry)

        when:
        ModuleComponentIdentifier componentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('mygroup', 'mymodule'), '1.0')
        ArtifactResolutionResult result = query
            .forComponents(componentIdentifier)
            .withArtifacts(selectedComponentType, selectedArtifactType)
            .execute()

        then:
        result
        result.components.size() == 1
        def componentResult = result.components.iterator().next()
        componentResult.id.displayName == componentIdentifier.displayName
        componentResult instanceof UnresolvedComponentResult
        UnresolvedComponentResult unresolvedComponentResult = (UnresolvedComponentResult) componentResult
        unresolvedComponentResult.failure instanceof IllegalArgumentException
        unresolvedComponentResult.failure.message == failureMessage

        where:
        givenComponentTypeRegistry | selectedComponentType | selectedArtifactType | failureMessage
        testComponentTypeRegistry  | UnknownComponent      | TestArtifact         | "Not a registered component type: ${UnknownComponent.name}."
        testComponentTypeRegistry  | TestComponent         | UnknownArtifact      | "Artifact type $UnknownArtifact.name is not registered for component type ${TestComponent.name}."
    }

    def "forModule is cumulative"() {
        withArtifactResolutionInteractions(2)

        given:
        def query = createArtifactResolutionQuery(testComponentTypeRegistry)

        when:
        def result = query
            .forModule("g1", "n1", "v1")
            .forModule("g2", "n2", "v2")
            .withArtifacts(TestComponent, TestArtifact)
            .execute()

        then:
        result.components*.id.displayName.containsAll(["g1:n1:v1", "g2:n2:v2"])
    }

    private def withArtifactResolutionInteractions(int numberOfComponentsToResolve = 1) {
        1 * resolveIvyFactory.create(_, _, _, _, _, _, _) >> repositoryChain
        1 * repositoryChain.artifactResolver >> artifactResolver
        1 * repositoryChain.componentResolver >> componentMetaDataResolver
        def state = Mock(ComponentGraphResolveState)

        def metadata = Mock(ComponentGraphResolveMetadata)
        _ * state.getMetadata() >> metadata
        _ * state.prepareForArtifactResolution() >> Mock(ComponentArtifactResolveState)
        _ * metadata.getModuleVersionId() >> Mock(ModuleVersionIdentifier)

        numberOfComponentsToResolve * componentMetaDataResolver.resolve(_, _, _) >> { ComponentIdentifier componentId, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult resolveResult ->
            resolveResult.resolved(state, Stub(ComponentGraphSpecificResolveState))
        }
    }

    private DefaultArtifactResolutionQuery createArtifactResolutionQuery(ComponentTypeRegistry componentTypeRegistry) {
        new DefaultArtifactResolutionQuery(configurationContainerInternal, { [] }, resolveIvyFactory, globalDependencyResolutionRules, componentTypeRegistry, AttributeTestUtil.attributesFactory(), artifactTypeRegistry, ruleExecutor)
    }

    private ComponentTypeRegistry createTestComponentTypeRegistry() {
        return Stub(ComponentTypeRegistry) {
            getComponentRegistration(_) >> { Class componentType ->
                if (componentType == TestComponent) {
                    return createStubComponentRegistration()
                } else {
                    throw new IllegalArgumentException(String.format("Not a registered component type: %s.", componentType.getName()));
                }
            }
        }
    }

    private ComponentTypeRegistration createStubComponentRegistration() {
        return Stub(ComponentTypeRegistration) {
            getArtifactType(_) >> { Class artifactType ->
                throw new IllegalArgumentException(String.format("Artifact type %s is not registered for component type %s.", artifactType.getName(), TestComponent.getName()));
            }
        }
    }

    private static class TestComponent implements Component {
    }

    private static interface TestArtifact extends Artifact {
    }

    private static class UnknownComponent implements Component {
    }

    private static interface UnknownArtifact extends Artifact {
    }
}
