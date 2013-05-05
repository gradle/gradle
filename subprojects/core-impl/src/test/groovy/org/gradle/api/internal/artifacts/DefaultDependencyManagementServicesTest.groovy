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
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.cache.CacheRepository
import org.gradle.cache.DirectoryCacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.FileLockManager
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.Factory
import org.gradle.internal.TimeProvider
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.ListenerManager
import org.gradle.logging.LoggingManagerInternal
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

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
        Factory<LoggingManagerInternal> loggingFactory = Mock()
        _ * parent.getFactory(LoggingManagerInternal) >> loggingFactory
        ProgressLoggerFactory progressLoggerFactory = Mock()
        _ * parent.get(ProgressLoggerFactory) >> progressLoggerFactory
        CacheRepository cacheRepository = initCacheRepository()
        _ * parent.get(CacheRepository) >> cacheRepository
        ClassPathRegistry classPathRegistry = Mock()
        _ * parent.get(ClassPathRegistry) >> classPathRegistry
        _ * parent.get(ListenerManager) >> listenerManager
        _ * parent.get(FileLockManager) >> Mock(FileLockManager)
        _ * parent.get(TimeProvider) >> Mock(TimeProvider)
        _ * parent.get(TemporaryFileProvider) >> Mock(TemporaryFileProvider)
        _ * parent.get(ProjectAccessListener) >> Mock(ProjectAccessListener)
        _ * parent.get(TopLevelDependencyManagementServices) >> Mock(TopLevelDependencyManagementServices)
    }

    private CacheRepository initCacheRepository() {
        CacheRepository cacheRepository = Mock()
        DirectoryCacheBuilder cacheBuilder = Mock()
        _ * cacheRepository.store(_) >> cacheBuilder
        _ * cacheBuilder.withVersionStrategy(_) >> cacheBuilder
        _ * cacheBuilder.withLockMode(_) >> cacheBuilder
        _ * cacheBuilder.withDisplayName(_) >> cacheBuilder
        PersistentCache cache = Mock()
        _ * cacheBuilder.open() >> cache
        cache.baseDir >> new File("cache")
        return cacheRepository
    }

    def "can create dependency resolution services"() {
        given:
        _ * parent.get(Instantiator) >> instantiator
        _ * parent.get(StartParameter) >> startParameter
        1 * instantiator.newInstance(DefaultRepositoryHandler, _, _) >> repositoryHandler
        1 * instantiator.newInstance(DefaultConfigurationContainer, !null, instantiator,
                domainObjectContext, listenerManager, dependencyMetaDataProvider) >> configurationContainer
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

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def publishResolverHandler = resolutionServices.createArtifactPublicationServices().createRepositoryHandler()

        then:
        publishResolverHandler == publishRepositoryHandler
    }

    def "publish services provide an ArtifactPublisher"() {
        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def ivyService = resolutionServices.createArtifactPublicationServices().createArtifactPublisher()

        then:
        ivyService != null
    }
}
