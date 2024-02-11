/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.authentication.DefaultAuthenticationSchemeRegistry
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultBaseRepositoryFactoryTest extends Specification {
    final LocalMavenRepositoryLocator localMavenRepoLocator = Mock()
    final FileResolver fileResolver = Mock()
    final FileCollectionFactory fileCollectionFactory = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final DefaultArtifactIdentifierFileStore artifactIdentifierFileStore = Stub()
    final DefaultExternalResourceFileStore externalResourceFileStore = Stub()
    final MetaDataParser pomParser = Mock()
    final GradleModuleMetadataParser metadataParser = Mock()
    final ivyContextManager = Mock(IvyContextManager)
    final AuthenticationSchemeRegistry authenticationSchemeRegistry = new DefaultAuthenticationSchemeRegistry()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock()
    final MavenMutableModuleMetadataFactory mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()
    final IvyMutableModuleMetadataFactory ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory = new DefaultUrlArtifactRepository.Factory(fileResolver);
    final ProviderFactory providerFactory = Mock()

    final DefaultBaseRepositoryFactory factory = new DefaultBaseRepositoryFactory(
            localMavenRepoLocator, fileResolver, fileCollectionFactory, transportFactory, locallyAvailableResourceFinder,
            artifactIdentifierFileStore, externalResourceFileStore, pomParser, metadataParser, authenticationSchemeRegistry, ivyContextManager, moduleIdentifierFactory,
            TestUtil.instantiatorFactory(), Mock(FileResourceRepository), mavenMetadataFactory, ivyMetadataFactory, SnapshotTestUtil.isolatableFactory(), TestUtil.objectFactory(),
            CollectionCallbackActionDecorator.NOOP,
            urlArtifactRepositoryFactory,
            TestUtil.checksumService,
            providerFactory, new VersionParser()
    )

    def testCreateFlatDirResolver() {
        expect:
        def repo = factory.createFlatDirRepository()
        repo instanceof DefaultFlatDirArtifactRepository
    }

    def testCreateGoogleRepo() {
        given:
        def googleUrl = new URI(DefaultRepositoryHandler.GOOGLE_URL)

        when:
        fileResolver.resolveUri(DefaultRepositoryHandler.GOOGLE_URL) >> googleUrl

        then:
        def repo = factory.createGoogleRepository()
        repo instanceof DefaultMavenArtifactRepository
        repo.url == googleUrl
    }

    def testCreateLocalMavenRepo() {
        given:
        File repoDir = new File(".m2/repository")

        when:
        localMavenRepoLocator.getLocalMavenRepository() >> repoDir
        fileResolver.resolveUri(repoDir) >> repoDir.toURI()

        then:
        def repo = factory.createMavenLocalRepository()
        repo instanceof DefaultMavenLocalArtifactRepository
        repo.url == repoDir.toURI()
    }

    def testCreateJCenterRepo() {
        given:
        def jcenterUrl = new URI(DefaultRepositoryHandler.BINTRAY_JCENTER_URL)

        when:
        fileResolver.resolveUri(DefaultRepositoryHandler.BINTRAY_JCENTER_URL) >> jcenterUrl

        then:
        def repo = factory.createJCenterRepository()
        repo instanceof DefaultMavenArtifactRepository
        repo.url == jcenterUrl
    }

    def testCreateMavenCentralRepo() {
        given:
        def centralUrl = new URI(RepositoryHandler.MAVEN_CENTRAL_URL)

        when:
        fileResolver.resolveUri(RepositoryHandler.MAVEN_CENTRAL_URL) >> centralUrl

        then:
        def repo = factory.createMavenCentralRepository()
        repo instanceof DefaultMavenArtifactRepository
        repo.url == centralUrl
    }

    def createIvyRepository() {
        expect:
        def repo = factory.createIvyRepository()
        repo instanceof DefaultIvyArtifactRepository
    }

    def createMavenRepository() {
        expect:
        def repo = factory.createMavenRepository()
        repo instanceof DefaultMavenArtifactRepository
    }
}
