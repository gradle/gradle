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

package org.gradle.api.internal.dependencies

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.InvalidUserDataException
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.URLResolver
import org.gradle.api.DependencyManager
import org.apache.ivy.core.cache.RepositoryCacheManager
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver

/**
 * @author Hans Dockter
 */
class ResolverFactoryTest extends GroovyTestCase {
    static final String RESOLVER_URL = 'http://a.b.c/'
    static final Map RESOLVER_MAP = [name: 'mapresolver', url: 'http://x.y.z/']
    static final IBiblioResolver TEST_RESOLVER = new IBiblioResolver()
    static {
        TEST_RESOLVER.name = 'ivyResolver'
    }

    static final String TEST_REPO_NAME = 'reponame'
    static final String TEST_REPO_URL = 'http://www.gradle.org'

    LocalReposCacheHandler localReposCacheHandler

    RepositoryCacheManager dummyCacheManager = new DefaultRepositoryCacheManager()

    ResolverFactory factory

    void setUp() {
        factory = new ResolverFactory()
    }

    void testInit() {
        assert factory.localReposCacheHandler.is(localReposCacheHandler)
    }

    void testCreateResolver() {
        checkIBibiblioResolver(factory.createResolver(RESOLVER_URL), RESOLVER_URL, RESOLVER_URL)
        checkIBibiblioResolver(factory.createResolver(RESOLVER_MAP), RESOLVER_MAP.name, RESOLVER_MAP.url)
        DependencyResolver resolver = factory.createResolver(TEST_RESOLVER)
        assert resolver.is(TEST_RESOLVER)
        def someIllegalDescription = new NullPointerException()
        shouldFail(InvalidUserDataException) {
            factory.createResolver(someIllegalDescription)
        }
    }

    private void checkIBibiblioResolver(DualResolver resolver, String name, String url) {
        assertEquals url, resolver.ivyResolver.root
        assertEquals name, resolver.name
    }

    public void testCreateMavenRepo() {
        String testUrl2 = 'http://www.gradle2.org'
        DualResolver dualResolver = factory.createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL, testUrl2)
        IBiblioResolver iBiblioResolver = dualResolver.ivyResolver
        assert iBiblioResolver.usepoms
        assert iBiblioResolver.m2compatible
        assertEquals(TEST_REPO_URL + '/', iBiblioResolver.root)
        assertEquals(DependencyManager.MAVEN_REPO_PATTERN, iBiblioResolver.pattern)
        assertEquals("${TEST_REPO_NAME}_poms", iBiblioResolver.name)
        URLResolver urlResolver = dualResolver.artifactResolver
        assert urlResolver.m2compatible
        assert urlResolver.artifactPatterns.contains("$TEST_REPO_URL/$DependencyManager.MAVEN_REPO_PATTERN" as String)
        assert urlResolver.artifactPatterns.contains("$testUrl2/$DependencyManager.MAVEN_REPO_PATTERN" as String)
        assertEquals("${TEST_REPO_NAME}_jars", urlResolver.name)
    }

    void testCreateFlatDirResolver() {
        factory.localReposCacheHandler = [getCacheManager: {dummyCacheManager}] as LocalReposCacheHandler
        File dir1 = new File('/rootFolder')
        File dir2 = new File('/rootFolder2')
        String expectedName = 'libs'
        FileSystemResolver resolver = factory.createFlatDirResolver(expectedName, [dir1, dir2] as File[])
        checkNoModuleRepository(resolver, expectedName,
                [dir1, dir2].collect {"$it.absolutePath/$DependencyManager.FLAT_DIR_RESOLVER_PATTERN"})
    }

    private void checkNoModuleRepository(RepositoryResolver resolver, String expectedName, List expectedPatterns) {
        assertEquals(expectedName, resolver.name)
        assertEquals(expectedPatterns, resolver.ivyPatterns)
        assertEquals(expectedPatterns, resolver.artifactPatterns)
        assertTrue(resolver.allownomd)
        assert resolver.repositoryCacheManager == dummyCacheManager
    }

}
