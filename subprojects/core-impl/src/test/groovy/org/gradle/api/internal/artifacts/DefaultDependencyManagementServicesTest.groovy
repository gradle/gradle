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
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.PublishModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.externalresource.cached.ByUrlCachedExternalResourceIndex
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryCachedArtifactIndex
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.ivy.ArtifactRevisionIdFileStore
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.listener.ListenerManager
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.util.BuildCommencedTimeProvider
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
        _ * parent.get(ProgressLoggerFactory) >> Stub(ProgressLoggerFactory)
        _ * parent.get(Instantiator) >> instantiator
        _ * parent.get(StartParameter) >> startParameter
        _ * parent.get(ListenerManager) >> listenerManager
        _ * parent.get(IvyContextManager) >> Stub(IvyContextManager)
        _ * parent.get(CacheLockingManager) >> Stub(CacheLockingManager)
        _ * parent.get(ArtifactRevisionIdFileStore) >> Stub(ArtifactRevisionIdFileStore)
        _ * parent.get(ModuleResolutionCache) >> Stub(ModuleResolutionCache)
        _ * parent.get(ModuleMetaDataCache) >> Stub(ModuleMetaDataCache)
        _ * parent.get(ArtifactAtRepositoryCachedArtifactIndex) >> Stub(ArtifactAtRepositoryCachedArtifactIndex)
        _ * parent.get(BuildCommencedTimeProvider) >> Stub(BuildCommencedTimeProvider)
        _ * parent.get(InMemoryDependencyMetadataCache) >> Stub(InMemoryDependencyMetadataCache)
        _ * parent.get(ByUrlCachedExternalResourceIndex) >> Stub(ByUrlCachedExternalResourceIndex)
        _ * parent.get(PublishModuleDescriptorConverter) >> Stub(PublishModuleDescriptorConverter)
        _ * parent.get(DependencyFactory) >> Stub(DependencyFactory)
        _ * parent.get(LocalMavenRepositoryLocator) >> Stub(LocalMavenRepositoryLocator)
        _ * parent.get(LocallyAvailableResourceFinder) >> Stub(LocallyAvailableResourceFinder)
        _ * parent.get(VersionMatcher) >> Stub(VersionMatcher)
        _ * parent.get(LatestStrategy) >> Stub(LatestStrategy)
        _ * parent.get(ResolverStrategy) >> Stub(ResolverStrategy)
    }

    def "can create dependency resolution services"() {
        given:
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
