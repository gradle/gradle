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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers
import org.gradle.api.internal.component.ComponentTypeRegistration
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DefaultArtifactResolutionQueryTest extends Specification {
    def configurationContainerInternal = Stub(ConfigurationContainerInternal)
    def repositoryHandler = Stub(RepositoryHandler)
    def resolveIvyFactory = Mock(ResolveIvyFactory)
    def globalDependencyResolutionRules = Mock(GlobalDependencyResolutionRules)
    def cacheLockingManager = Mock(CacheLockingManager)
    def componentTypeRegistry = Mock(ComponentTypeRegistry)
    def artifactResolver = Mock(ArtifactResolver)
    def repositoryChain = Mock(ComponentResolvers)
    def componentMetaDataResolver = Mock(ComponentMetaDataResolver)
    def componentResolveMetaData = Mock(ComponentResolveMetadata)

    @Shared ComponentTypeRegistry testComponentTypeRegistry = createTestComponentTypeRegistry()

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

    @Unroll
    def "invalid component type #selectedComponentType and artifact type #selectedArtifactType is wrapped in UnresolvedComponentResult"() {
        def query = createArtifactResolutionQuery(givenComponentTypeRegistry)

        when:
        ModuleComponentIdentifier componentIdentifier = new DefaultModuleComponentIdentifier('mygroup', 'mymodule', '1.0')
        ArtifactResolutionResult result = query.forComponents(componentIdentifier).withArtifacts(selectedComponentType, selectedArtifactType).execute()

        then:
        1 * cacheLockingManager.useCache(_) >> { Factory action ->
            action.create()
        }
        1 * resolveIvyFactory.create(_, _, _) >> repositoryChain
        1 * repositoryChain.artifactResolver >> artifactResolver
        1 * repositoryChain.componentResolver >> componentMetaDataResolver
        1 * componentMetaDataResolver.resolve(_, _, _) >> { ComponentIdentifier componentId, ComponentOverrideMetadata requestMetaData, BuildableComponentResolveResult resolveResult ->
            resolveResult.resolved(componentResolveMetaData)
        }
        result
        result.components.size() == 1
        def componentResult = result.components.iterator().next()
        componentResult.id.displayName == componentIdentifier.displayName
        componentResult instanceof UnresolvedComponentResult
        UnresolvedComponentResult unresolvedComponentResult = (UnresolvedComponentResult)componentResult
        unresolvedComponentResult.failure instanceof IllegalArgumentException
        unresolvedComponentResult.failure.message == failureMessage

        where:
        givenComponentTypeRegistry | selectedComponentType | selectedArtifactType   | failureMessage
        testComponentTypeRegistry  | UnknownComponent      | TestArtifact           | "Not a registered component type: ${UnknownComponent.name}."
        testComponentTypeRegistry  | TestComponent         | UnknownArtifact        | "Artifact type $UnknownArtifact.name is not registered for component type ${TestComponent.name}."
    }

    private DefaultArtifactResolutionQuery createArtifactResolutionQuery(ComponentTypeRegistry componentTypeRegistry) {
        new DefaultArtifactResolutionQuery(configurationContainerInternal, repositoryHandler, resolveIvyFactory, globalDependencyResolutionRules, cacheLockingManager, componentTypeRegistry)
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
