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

package org.gradle.internal.resource.cached

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import org.gradle.util.BuildCommencedTimeProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DefaultArtifactResolutionCacheTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()

    BuildCommencedTimeProvider timeProvider = Stub(BuildCommencedTimeProvider) {
        getCurrentTime() >> 0
    }

    def cacheLockingManager = Stub(CacheLockingManager) {
        useCache(_, _) >> { displayName, action ->
            if (action instanceof org.gradle.internal.Factory) {
                return action.create()
            } else {
                action.run()
            }
        }

        createCache(_, _, _) >> { String file, Serializer keySerializer, Serializer valueSerializer ->
            return new InMemoryIndexedCache<>(valueSerializer)
        }
    }
    
    DefaultCachedExternalResourceIndex<String> index

    def setup() {
        index = new DefaultCachedExternalResourceIndex("index", String, timeProvider, cacheLockingManager)
    }

    @Unroll "stores entry - lastModified = #lastModified"() {
        given:
        def key = "key"
        def artifactFile = tmp.createFile("artifact") << "content"
        
        when:
        index.store(key, artifactFile, new DefaultExternalResourceMetaData(new URI("abc"), lastModified, 100, null, null, null))
        
        then:
        def cached = index.lookup(key)
        
        and:
        cached != null
        cached.cachedFile == artifactFile
        cached.externalResourceMetaData != null
        cached.externalResourceMetaData.lastModified == lastModified
        cached.externalResourceMetaData.location == new URI("abc")


        where:
        lastModified << [new Date(), null]
    }

}
