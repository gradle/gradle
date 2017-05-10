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

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.internal.InstantiatorFactory
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualArtifactPublisher
import org.gradle.api.internal.artifacts.ivyservice.publisher.IvyBackedArtifactPublisher
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.lang.reflect.ParameterizedType

class DefaultDependencyManagementServicesTest extends Specification {
    final ServiceRegistry parent = Mock()
    final DefaultDependencyManagementServices services = new DefaultDependencyManagementServices(parent)

    def setup() {
        _ * parent.get(Instantiator) >> TestUtil.instantiatorFactory().decorate()
        _ * parent.get(InstantiatorFactory) >> TestUtil.instantiatorFactory()
        _ * parent.get({it instanceof Class}) >> { Class t -> Stub(t) }
        _ * parent.get({it instanceof ParameterizedType}) >> { ParameterizedType t -> Stub(t.rawType) }
        _ * parent.getAll({it instanceof Class}) >> { Class t -> [Stub(t)]}
        _ * parent.hasService(_) >> true
    }

    def "can create dependency resolution DSL services"() {
        given:
        def registry = new DefaultServiceRegistry(parent)

        when:
        registry.register({ ServiceRegistration registration -> services.addDslServices(registration) } as Action)
        def resolutionServices = registry.get(DependencyResolutionServices)

        then:
        resolutionServices.resolveRepositoryHandler instanceof DefaultRepositoryHandler
        resolutionServices.configurationContainer instanceof DefaultConfigurationContainer
        resolutionServices.dependencyHandler instanceof DefaultDependencyHandler
        registry.get(ComponentMetadataHandler) instanceof DefaultComponentMetadataHandler
        registry.get(ArtifactHandler) instanceof DefaultArtifactHandler
        registry.get(BaseRepositoryFactory) instanceof DefaultBaseRepositoryFactory
    }

    def "publish services provide a repository handler"() {
        given:
        def registry = new DefaultServiceRegistry(parent)

        when:
        registry.register({ ServiceRegistration registration -> services.addDslServices(registration) } as Action)
        def publishServices = registry.get(ArtifactPublicationServices)

        then:
        def publishResolverHandler = publishServices.createRepositoryHandler()
        publishResolverHandler instanceof DefaultRepositoryHandler
        !publishResolverHandler.is(publishServices.createRepositoryHandler())
    }

    def "publish services provide an ArtifactPublisher"() {
        given:
        def registry = new DefaultServiceRegistry(parent)

        when:
        registry.register({ ServiceRegistration registration -> services.addDslServices(registration) } as Action)
        def publishServices = registry.get(ArtifactPublicationServices)

        then:
        def ivyService = publishServices.createArtifactPublisher()
        ivyService instanceof IvyContextualArtifactPublisher
        ivyService.delegate instanceof IvyBackedArtifactPublisher
        !ivyService.is(publishServices.createArtifactPublisher())
    }
}
