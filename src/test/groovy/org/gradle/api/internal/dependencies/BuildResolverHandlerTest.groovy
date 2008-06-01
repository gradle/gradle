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

import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.DependencyManager
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager

/**
 * @author Hans Dockter
 */
class BuildResolverHandlerTest extends GroovyTestCase {
    BuildResolverHandler handler

    LocalReposCacheHandler localReposCacheHandler

    DefaultRepositoryCacheManager dummyCacheManager = new DefaultRepositoryCacheManager()

    File buildResolverDir

    void setUp() {
        localReposCacheHandler = new LocalReposCacheHandler()
        buildResolverDir = new File('buildResolver')
        handler = new BuildResolverHandler(localReposCacheHandler)
        handler.buildResolverDir = buildResolverDir
    }

    void testInit() {
        assertEquals(buildResolverDir, handler.buildResolverDir)
        assertEquals(localReposCacheHandler, handler.localReposCacheHandler)
    }

    void testGetBuildResolver() {
        handler.localReposCacheHandler = [getCacheManager: {dummyCacheManager}] as LocalReposCacheHandler
        FileSystemResolver buildResolver = handler.buildResolver
        // check lazy init
        assert buildResolver
        assert buildResolver.is(handler.buildResolver)
        checkNoModuleRepository(buildResolver, DependencyManager.BUILD_RESOLVER_NAME,
                ["$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"])
    }

    private void checkNoModuleRepository(RepositoryResolver resolver, String expectedName, List expectedPatterns) {
        assertEquals(expectedName, resolver.name)
        assertEquals(expectedPatterns, resolver.ivyPatterns)
        assertEquals(expectedPatterns, resolver.artifactPatterns)
        assertTrue(resolver.allownomd)
        assert resolver.repositoryCacheManager == dummyCacheManager
    }
}
