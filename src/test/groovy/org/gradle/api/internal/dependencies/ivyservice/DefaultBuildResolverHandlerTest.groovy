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

package org.gradle.api.internal.dependencies.ivyservice

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.internal.dependencies.ivyservice.DefaultBuildResolverHandler
import org.gradle.api.internal.dependencies.ivyservice.LocalReposCacheHandler
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class DefaultBuildResolverHandlerTest {
    DefaultBuildResolverHandler handler

    LocalReposCacheHandler localReposCacheHandler

    DefaultRepositoryCacheManager dummyCacheManager = new DefaultRepositoryCacheManager()

    File buildResolverDir

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        localReposCacheHandler = new LocalReposCacheHandler()
        buildResolverDir = new File('buildResolver')
        handler = new DefaultBuildResolverHandler(buildResolverDir, localReposCacheHandler)
    }

    @Test public void testInit() {
        assertEquals(buildResolverDir, handler.buildResolverDir)
        assertEquals(localReposCacheHandler, handler.localReposCacheHandler)
    }

    @Test public void testGetBuildResolver() {
        handler.localReposCacheHandler = context.mock(LocalReposCacheHandler)
        context.checking {
            one(handler.localReposCacheHandler).getCacheManager(new File(buildResolverDir, DependencyManager.DEFAULT_CACHE_DIR_NAME))
            will(returnValue(dummyCacheManager))
        }
        FileSystemResolver buildResolver = handler.buildResolver
        // check lazy init
        assert buildResolver
        assert buildResolver.is(handler.buildResolver)
        checkNoModuleRepository(buildResolver, DependencyManager.BUILD_RESOLVER_NAME,
                ["$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"])
    }

    private void checkNoModuleRepository(RepositoryResolver resolver, String expectedName, List expectedPatterns) {
        assertEquals(expectedName, resolver.name)
        assert expectedPatterns == resolver.ivyPatterns
        assert expectedPatterns == resolver.artifactPatterns
        assertTrue(resolver.allownomd)
        assert resolver.repositoryCacheManager == dummyCacheManager
    }
}
