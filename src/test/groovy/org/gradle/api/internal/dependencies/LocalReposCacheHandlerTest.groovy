/*
 * Copyright 2007-2008 the original author or authors.
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

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.plugins.lock.NoLockStrategy
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class LocalReposCacheHandlerTest extends GroovyTestCase {
     void testGetCacheManager() {
         File buildResolverDir = '/somedir' as File
         LocalReposCacheHandler cacheHandler = new LocalReposCacheHandler(buildResolverDir)
         DefaultRepositoryCacheManager cacheManager = cacheHandler.cacheManager
         assert cacheManager.is(cacheHandler.cacheManager)
         assert cacheManager.useOrigin
         assert cacheManager.name == DependencyManager.DEFAULT_CACHE_NAME
         assertEquals(DependencyManager.DEFAULT_CACHE_IVY_PATTERN, cacheManager.ivyPattern)
         assert cacheManager.artifactPattern == DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN
         assertEquals(new File(buildResolverDir, DependencyManager.DEFAULT_CACHE_DIR_NAME), cacheManager.basedir)
         assert cacheManager.lockStrategy instanceof NoLockStrategy
     }
}
