/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.cached

import org.gradle.CacheUsage
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.internal.TimeProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultArtifactResolutionCacheTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()

    Date time = new Date(0)
    TimeProvider timeProvider = new TimeProvider() {
        long getCurrentTime() { time.getTime() }
    }

    CacheFactory cacheFactory = new InMemoryCacheFactory()
    DefaultCacheRepository cacheRepository
    DefaultCacheLockingManager cacheLockingManager
    
    DefaultCachedExternalResourceIndex<String> index

    def setup() {
        cacheRepository = new DefaultCacheRepository(tmp.createDir('user-home'), tmp.createDir('project-cache'), CacheUsage.ON, cacheFactory)
        cacheLockingManager = new DefaultCacheLockingManager(cacheRepository)
        index = new DefaultCachedExternalResourceIndex(tmp.createFile("index"), String, timeProvider, cacheLockingManager)
    }

    @Unroll "stores entry - lastModified = #lastModified, artifactUrl = #artifactUrl"() {
        given:
        def key = "key"
        def artifactFile = tmp.createFile("artifact") << "content"
        
        when:
        index.store(key, artifactFile, new DefaultExternalResourceMetaData(artifactUrl, lastModified, 100, null, null))
        
        then:
        def cached = index.lookup(key)
        
        and:
        cached != null
        cached.cachedFile == artifactFile
        cached.externalResourceMetaData != null
        cached.externalResourceMetaData.lastModified == lastModified
        cached.externalResourceMetaData.location == artifactUrl


        where:
        lastModified | artifactUrl
        new Date()   | null
        null         | "abc"
        null         | null
        new Date()   | "abc"
    }

}
