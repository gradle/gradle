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

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.JUnit4GroovyMockery
import org.hamcrest.Matchers
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
class DefaultBaseRepositoryFactoryTest {
    static final URI RESOLVER_URL = new URI('http://a.b.c/')
    static final String TEST_REPO_NAME = 'reponame'
    static final String TEST_REPO = 'http://www.gradle.org'
    static final URI TEST_REPO_URL = new URI('http://www.gradle.org/')
    static final URI TEST_REPO2_URL = new URI('http://www.gradleware.com/')

    final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    final LocalMavenRepositoryLocator localMavenRepoLocator = context.mock(LocalMavenRepositoryLocator.class)
    final FileResolver fileResolver = context.mock(FileResolver.class)
    final RepositoryTransportFactory transportFactory = context.mock(RepositoryTransportFactory.class)
    final LocallyAvailableResourceFinder locallyAvailableResourceFinder = context.mock(LocallyAvailableResourceFinder.class)
    final CachedExternalResourceIndex cachedExternalResourceIndex = context.mock(CachedExternalResourceIndex);


    final DefaultBaseRepositoryFactory factory = new DefaultBaseRepositoryFactory(
            localMavenRepoLocator, fileResolver, new DirectInstantiator(), transportFactory, locallyAvailableResourceFinder, cachedExternalResourceIndex
    )

    @Before public void setup() {
        context.checking {
            allowing(fileResolver).resolveUri('uri');
            will(returnValue(RESOLVER_URL))
            allowing(fileResolver).resolveUri(TEST_REPO);
            will(returnValue(TEST_REPO_URL))
            allowing(fileResolver).resolveUri('uri2');
            will(returnValue(TEST_REPO2_URL))
            allowing(fileResolver).resolveUri(withParam(Matchers.instanceOf(URI)));
            will { uri -> return uri }
        }
    }

    @Test public void testCreateResolverWithStringDescription() {
        def repository = factory.createRepository('uri')

        assert repository instanceof DefaultMavenArtifactRepository
        assert repository.url == RESOLVER_URL
        assert repository.name == null
        assert repository.artifactUrls.isEmpty()
    }

    @Test public void testCreateResolverWithMapDescription() {
        def repository = factory.createRepository([name: 'name', url: 'uri'])

        assert repository instanceof DefaultMavenArtifactRepository
        assert repository.url == RESOLVER_URL
        assert repository.name == 'name'
        assert repository.artifactUrls.isEmpty()
    }

    @Test public void testCreateResolverWithResolverDescription() {
        DependencyResolver resolver = context.mock(DependencyResolver)
        
        ArtifactRepository repository = factory.createRepository(resolver)

        assert repository instanceof FixedResolverArtifactRepository
        assert repository.resolver == resolver
    }

    @Test public void testCreateResolverWithArtifactRepositoryDescription() {
        ArtifactRepository repo = context.mock(ArtifactRepository)

        assert factory.createRepository(repo) == repo
    }

    @Test(expected = InvalidUserDataException) public void testCreateResolverForUnknownDescription() {
        def someIllegalDescription = new NullPointerException()
        factory.createRepository(someIllegalDescription)
    }

    @Test public void testCreateFlatDirResolver() {
        def repo =factory.createFlatDirRepository()
        assert repo instanceof DefaultFlatDirArtifactRepository
    }

    @Test public void testCreateLocalMavenRepo() {
        File repoDir = new File(".m2/repository")

        context.checking {
            one(localMavenRepoLocator).getLocalMavenRepository()
            will(returnValue(repoDir))
            allowing(fileResolver).resolveUri(repoDir)
            will(returnValue(repoDir.toURI()))
        }

        def repo = factory.createMavenLocalRepository()
        assert repo instanceof DefaultMavenArtifactRepository
        assert repo.url == repoDir.toURI()
    }

    @Test public void testCreateMavenCentralRepo() {
        def centralUrl = new URI(RepositoryHandler.MAVEN_CENTRAL_URL)

        context.checking {
            allowing(fileResolver).resolveUri(RepositoryHandler.MAVEN_CENTRAL_URL)
            will(returnValue(centralUrl))
        }

        def repo = factory.createMavenCentralRepository()
        assert repo instanceof DefaultMavenArtifactRepository
        assert repo.url == centralUrl
    }

    @Test public void createIvyRepository() {
        def repo = factory.createIvyRepository()
        assert repo instanceof DefaultIvyArtifactRepository
    }

    @Test public void createMavenRepository() {
        def repo = factory.createMavenRepository()
        assert repo instanceof DefaultMavenArtifactRepository
    }
}
