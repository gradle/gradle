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

import org.apache.ivy.core.cache.RepositoryCacheManager
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DefaultBaseRepositoryFactoryTest extends Specification {
    static final URI RESOLVER_URL = new URI('http://a.b.c/')
    static final String TEST_REPO = 'http://www.gradle.org'
    static final URI TEST_REPO_URL = new URI('http://www.gradle.org/')
    static final URI TEST_REPO2_URL = new URI('http://www.gradleware.com/')

    final LocalMavenRepositoryLocator localMavenRepoLocator = Mock()
    final FileResolver fileResolver = Mock()
    final RepositoryTransportFactory transportFactory = Mock()
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = Mock()
    final RepositoryCacheManager localCacheManager = Mock()
    final RepositoryCacheManager downloadingCacheManager = Mock()
    final ProgressLoggerFactory progressLoggerFactory = Mock()
    final MetaDataParser metaDataParser = Mock()
    final ModuleMetadataProcessor metadataProcessor = Mock()

    final DefaultBaseRepositoryFactory factory = new DefaultBaseRepositoryFactory(
            localMavenRepoLocator, fileResolver, new DirectInstantiator(), transportFactory, locallyAvailableResourceFinder,
            progressLoggerFactory, localCacheManager, downloadingCacheManager, metaDataParser, metadataProcessor
    )

//    @Before public void setup() {
//        fileResolver = Stub(FileResolver) {
//            resolveUri('uri') >> RESOLVER_URL
//            resolveUri(TEST_REPO) >> TEST_REPO_URL
//            resolveUri('uri2') >> TEST_REPO2_URL
//        }
//    }

    def testCreateResolverWithStringDescription() {
        when:
        fileResolver.resolveUri('uri') >> RESOLVER_URL

        then:
        def repository = factory.createRepository('uri')

        repository instanceof DefaultMavenArtifactRepository
        repository.name == null
        repository.url == RESOLVER_URL
        repository.artifactUrls.isEmpty()
    }

    def testCreateResolverWithMapDescription() {
        when:
        fileResolver.resolveUri('uri') >> RESOLVER_URL

        then:
        def repository = factory.createRepository([name: 'name', url: 'uri'])

        repository instanceof DefaultMavenArtifactRepository
        repository.name == 'name'
        repository.url == RESOLVER_URL
        repository.artifactUrls.isEmpty()
    }

    def testCreateResolverWithResolverDescription() {
        when:
        def resolver = Mock(DependencyResolver)

        then:
        ArtifactRepository repository = factory.createRepository(resolver)

        repository instanceof FixedResolverArtifactRepository
        repository.resolver == resolver
    }

    def testCreateResolverWithArtifactRepositoryDescription() {
        when:
        ArtifactRepository repo = Mock(ArtifactRepository)

        then:
        factory.createRepository(repo) == repo
    }

    def testCreateResolverForUnknownDescription() {
        when:
        def someIllegalDescription = new NullPointerException()

        factory.createRepository(someIllegalDescription)

        then:
        thrown InvalidUserDataException
    }

    def testCreateFlatDirResolver() {
        expect:
        def repo =factory.createFlatDirRepository()
        repo instanceof DefaultFlatDirArtifactRepository
    }

    def testCreateLocalMavenRepo() {
        given:
        File repoDir = new File(".m2/repository")

        when:
        localMavenRepoLocator.getLocalMavenRepository() >> repoDir
        fileResolver.resolveUri(repoDir) >> repoDir.toURI()

        then:
        def repo = factory.createMavenLocalRepository()
        repo instanceof DefaultMavenArtifactRepository
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
