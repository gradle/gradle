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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.result.ArtifactResolutionResult
import org.gradle.api.artifacts.result.UnresolvedComponentResult
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.component.ComponentTypeRegistry
import org.gradle.api.internal.component.DefaultComponentTypeRegistry
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentResolveMetaData
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.resolver.ArtifactResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import org.gradle.ivy.IvyDescriptorArtifact
import org.gradle.ivy.IvyModule
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.java.artifact.JavadocArtifact
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DefaultArtifactResolutionQueryTest extends Specification {
    def configurationContainerInternal = Mock(ConfigurationContainerInternal)
    def repositoryHandler = Stub(RepositoryHandler)
    def resolveIvyFactory = Mock(ResolveIvyFactory)
    def globalDependencyResolutionRules = Mock(GlobalDependencyResolutionRules)
    def cacheLockingManager = Mock(CacheLockingManager)
    def componentTypeRegistry = Mock(ComponentTypeRegistry)
    def artifactResolver = Mock(ArtifactResolver)
    def repositoryChain = Mock(RepositoryChain)
    def dependencyToComponentResolver = Mock(DependencyToComponentResolver)
    def componentResolveMetaData = Mock(ComponentResolveMetaData)

    @Shared ComponentTypeRegistry ivyComponentTypeRegistry = createIvyComponentTypeRegistry()
    @Shared ComponentTypeRegistry mavenComponentTypeRegistry = createMavenComponentTypeRegistry()

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
    def "invalid component type #selectedComponentType and artifact type #selectedArtifactType"() {
        def query = createArtifactResolutionQuery(givenComponentTypeRegistry)

        when:
        ModuleComponentIdentifier componentIdentifier = new DefaultModuleComponentIdentifier('mygroup', 'mymodule', '1.0')
        ArtifactResolutionResult result = query.forComponents(componentIdentifier).withArtifacts(selectedComponentType, selectedArtifactType).execute()

        then:
        1 * cacheLockingManager.useCache(_, _) >> { String operationDisplayName, Factory action ->
            action.create()
        }
        1 * resolveIvyFactory.create(_, _, _) >> repositoryChain
        1 * repositoryChain.artifactResolver >> artifactResolver
        1 * repositoryChain.dependencyResolver >> dependencyToComponentResolver
        1 * dependencyToComponentResolver.resolve(_, _) >> { DependencyMetaData dependency, BuildableComponentResolveResult resolveResult ->
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
        ivyComponentTypeRegistry   | JvmLibrary            | IvyDescriptorArtifact  | "Not a registered component type: ${JvmLibrary.name}."
        ivyComponentTypeRegistry   | IvyModule             | SourcesArtifact        | "Artifact type $SourcesArtifact.name is not registered for component type ${IvyModule.name}."
        ivyComponentTypeRegistry   | IvyModule             | JavadocArtifact        | "Artifact type $JavadocArtifact.name is not registered for component type ${IvyModule.name}."
        ivyComponentTypeRegistry   | IvyModule             | MavenPomArtifact       | "Artifact type $MavenPomArtifact.name is not registered for component type ${IvyModule.name}."
        mavenComponentTypeRegistry | JvmLibrary            | MavenPomArtifact       | "Not a registered component type: ${JvmLibrary.name}."
        mavenComponentTypeRegistry | MavenModule           | SourcesArtifact        | "Artifact type $SourcesArtifact.name is not registered for component type ${MavenModule.name}."
        mavenComponentTypeRegistry | MavenModule           | JavadocArtifact        | "Artifact type $JavadocArtifact.name is not registered for component type ${MavenModule.name}."
        mavenComponentTypeRegistry | MavenModule           | IvyDescriptorArtifact  | "Artifact type $IvyDescriptorArtifact.name is not registered for component type ${MavenModule.name}."
    }

    private DefaultArtifactResolutionQuery createArtifactResolutionQuery(ComponentTypeRegistry componentTypeRegistry) {
        new DefaultArtifactResolutionQuery(configurationContainerInternal, repositoryHandler, resolveIvyFactory, globalDependencyResolutionRules, cacheLockingManager, componentTypeRegistry)
    }

    private ComponentTypeRegistry createIvyComponentTypeRegistry() {
        createComponentTypeRegistry(IvyModule, IvyDescriptorArtifact, ArtifactType.IVY_DESCRIPTOR)
    }

    private ComponentTypeRegistry createMavenComponentTypeRegistry() {
        createComponentTypeRegistry(MavenModule, MavenPomArtifact, ArtifactType.MAVEN_POM)
    }

    private ComponentTypeRegistry createComponentTypeRegistry(Class<? extends Component> componentType, Class<? extends Artifact> artifact, ArtifactType artifactType) {
        ComponentTypeRegistry componentTypeRegistry = new DefaultComponentTypeRegistry()
        componentTypeRegistry.maybeRegisterComponentType(componentType).registerArtifactType(artifact, artifactType)
        componentTypeRegistry
    }
}
