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

/**
 * @author Hans Dockter
 */
class SpecialResolverHandlerTest extends GroovyTestCase {
    SpecialResolverHandler handler

    File buildResolverDir

    void setUp() {
        buildResolverDir = new File('buildResolver')
        handler = new SpecialResolverHandler(buildResolverDir)
    }

    void testInit() {
        assertEquals(buildResolverDir, handler.buildResolverDir)
    }

    //
//    void testGetCacheManager() {
//        File
//        DefaultRepositoryCacheManager cacheManager = converter.getCacheManager()
//        assert cacheManager.is(converter.getCacheManager())
//        cacheManager.basedir = new File(buildResolverDir, 'cache')
//        cacheManager.name = 'build-resolver-cache'
//        cacheManager.useOrigin = true
//        cacheManager.lockStrategy = new NoLockStrategy()
//        cacheManager
//    }
//
    void testGetBuildResolver() {
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
        assert resolver.repositoryCacheManager == handler.cacheManager
    }

    void testCreateFlatDirResolver() {
        File dir1 = new File('/rootFolder')
        File dir2 = new File('/rootFolder2')
        String expectedName = 'libs'
        FileSystemResolver resolver = handler.createFlatDirResolver(expectedName, [dir1, dir2] as File[])
        checkNoModuleRepository(resolver, expectedName,
                [dir1, dir2].collect{"$it.absolutePath/$DependencyManager.FLAT_DIR_RESOLVER_PATTERN"})
    }
}
