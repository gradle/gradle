/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.StartParameter
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.ListenerManager
import spock.lang.Specification

import java.lang.reflect.ParameterizedType

class DefaultDependencyManagementServicesTest extends Specification {
    final ServiceRegistry parent = Mock()
    final FileResolver fileResolver = Mock()
    final DependencyMetaDataProvider dependencyMetaDataProvider = Mock()
    final ProjectFinder projectFinder = Mock()
    final Instantiator instantiator = Mock()
    final DomainObjectContext domainObjectContext = Mock()
    final DefaultRepositoryHandler repositoryHandler = Mock()
    final ConfigurationContainerInternal configurationContainer = Mock()
    final StartParameter startParameter = Mock()
    final ListenerManager listenerManager = Mock()
    final DefaultDependencyManagementServices services = new DefaultDependencyManagementServices(parent)

    def setup() {
        _ * parent.get(Instantiator) >> instantiator
        _ * parent.get(StartParameter) >> startParameter
        _ * parent.get(ListenerManager) >> listenerManager
        _ * parent.get({it instanceof Class}) >> { Class t -> Stub(t) }
        _ * parent.get({it instanceof ParameterizedType}) >> { ParameterizedType t -> Stub(t.rawType) }
    }

    def "can create dependency resolution services"() {
        given:
        1 * instantiator.newInstance(DefaultRepositoryHandler, _, _) >> repositoryHandler
        1 * instantiator.newInstance(DefaultConfigurationContainer, !null, instantiator,
                domainObjectContext, listenerManager, dependencyMetaDataProvider) >> configurationContainer
        1 * instantiator.newInstance(DefaultComponentMetadataHandler, _) >> Stub(DefaultComponentMetadataHandler)
        def strategy = new DefaultResolutionStrategy()
        instantiator.newInstance(DefaultResolutionStrategy) >> strategy

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)

        then:
        resolutionServices.resolveRepositoryHandler
        resolutionServices.configurationContainer
        resolutionServices.dependencyHandler
        resolutionServices.artifactHandler
        resolutionServices.createArtifactPublicationServices()
    }

    def "publish services provide a repository handler"() {
        DefaultRepositoryHandler publishRepositoryHandler = Mock()

        given:
        _ * parent.get(Instantiator) >> instantiator
        _ * instantiator.newInstance(DefaultRepositoryHandler, _, _) >> publishRepositoryHandler
        _ * instantiator.newInstance(DefaultComponentMetadataHandler, _) >> Stub(DefaultComponentMetadataHandler)

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def publishServices = resolutionServices.createArtifactPublicationServices()

        then:
        def publishResolverHandler = publishServices.createRepositoryHandler()
        publishResolverHandler == publishRepositoryHandler
    }

    def "publish services provide an ArtifactPublisher"() {
        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def publishServices = resolutionServices.createArtifactPublicationServices()

        then:
        def ivyService = publishServices.createArtifactPublisher()
        ivyService != null
        ivyService != publishServices.createArtifactPublisher()
    }
}
