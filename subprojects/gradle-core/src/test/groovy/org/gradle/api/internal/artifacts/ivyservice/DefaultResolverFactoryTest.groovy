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

package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import org.apache.ivy.plugins.resolver.*
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.api.internal.Factory

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
class DefaultResolverFactoryTest {
    static final String RESOLVER_URL = 'http://a.b.c/'
    static final Map RESOLVER_MAP = [name: 'mapresolver', url: 'http://x.y.z/']
    static final IBiblioResolver TEST_RESOLVER = new IBiblioResolver()
    static {
        TEST_RESOLVER.name = 'ivyResolver'
    }

    static final String TEST_REPO_NAME = 'reponame'
    static final String TEST_REPO_URL = 'http://www.gradle.org'
    static final File TEST_CACHE_DIR = 'somepath' as File

    final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    final LocalMavenCacheLocator localMavenCacheLocator = context.mock(LocalMavenCacheLocator.class)
    final DefaultResolverFactory factory = new DefaultResolverFactory(context.mock(Factory.class), localMavenCacheLocator)

    @Test(expected = InvalidUserDataException) public void testCreateResolver() {
        checkMavenResolver(factory.createResolver(RESOLVER_URL), RESOLVER_URL, RESOLVER_URL)
        checkMavenResolver(factory.createResolver(RESOLVER_MAP), RESOLVER_MAP.name, RESOLVER_MAP.url)
        DependencyResolver resolver = factory.createResolver(TEST_RESOLVER)
        assert resolver.is(TEST_RESOLVER)
        def someIllegalDescription = new NullPointerException()
        factory.createResolver(someIllegalDescription)
    }

    private void checkMavenResolver(IBiblioResolver resolver, String name, String url) {
        assertEquals url, resolver.root
        assertEquals name, resolver.name
        assertTrue resolver.allownomd
    }

    @Test
    public void testCreateMavenRepoWithAdditionalJarUrls() {
        String testUrl2 = 'http://www.gradle2.org'
        DualResolver dualResolver = factory.createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL, testUrl2)
        assertTrue dualResolver.allownomd
        checkIBiblio(dualResolver.ivyResolver, "_poms")
        URLResolver urlResolver = dualResolver.artifactResolver
        assert urlResolver.m2compatible
        assert urlResolver.artifactPatterns.contains("$TEST_REPO_URL/$ResolverContainer.MAVEN_REPO_PATTERN" as String)
        assert urlResolver.artifactPatterns.contains("$testUrl2/$ResolverContainer.MAVEN_REPO_PATTERN" as String)
        assertEquals("${TEST_REPO_NAME}_jars" as String, urlResolver.name)
    }

    @Test
    public void testCreateMavenRepoWithNoAdditionalJarUrls() {
        checkIBiblio(factory.createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL), "")
    }

    private void checkIBiblio(IBiblioResolver iBiblioResolver, String expectedNameSuffix) {
        assert iBiblioResolver.usepoms
        assert iBiblioResolver.m2compatible
        assertTrue iBiblioResolver.allownomd
        assertEquals(TEST_REPO_URL + '/', iBiblioResolver.root)
        assertEquals(ResolverContainer.MAVEN_REPO_PATTERN, iBiblioResolver.pattern)
        assertEquals("${TEST_REPO_NAME}$expectedNameSuffix" as String, iBiblioResolver.name)
    }

    @Test public void testCreateFlatDirResolver() {
        File dir1 = new File('/rootFolder')
        File dir2 = new File('/rootFolder2')
        String expectedName = 'libs'
        FileSystemResolver resolver = factory.createFlatDirResolver(expectedName, [dir1, dir2] as File[])
        checkNoModuleRepository(resolver, expectedName,
                [dir1, dir2].collect {"$it.absolutePath/$ResolverContainer.FLAT_DIR_RESOLVER_PATTERN"}, [])
        assertEquals(new File(System.getProperty('java.io.tmpdir')).getCanonicalPath(),
                new File(((DefaultRepositoryCacheManager) resolver.getRepositoryCacheManager()).getBasedir().getParent()).getCanonicalPath())

    }

    @Test public void testCreateLocalMavenRepo() {
        File repoDir = new File(".m2/repository")

        context.checking {
            one(localMavenCacheLocator).getLocalMavenCache()
            will(returnValue(repoDir))
        }

        def repo = factory.createMavenLocalResolver('name')
        assertThat(repo, instanceOf(GradleIBiblioResolver))
        assertThat(repo.root, equalTo(repoDir.toURI().toString() + '/'))
    }

    private void checkNoModuleRepository(RepositoryResolver resolver, String expectedName, List expectedArtifactPatterns,
                                         List expectedIvyPatterns) {
        assertEquals(expectedName, resolver.name)
        assertEquals(expectedIvyPatterns, resolver.ivyPatterns)
        assert expectedArtifactPatterns == resolver.artifactPatterns
        assertTrue(resolver.allownomd)
    }
}
