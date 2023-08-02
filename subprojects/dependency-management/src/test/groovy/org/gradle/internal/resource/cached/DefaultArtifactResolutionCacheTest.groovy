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


import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinatorStub
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultArtifactResolutionCacheTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    BuildCommencedTimeProvider timeProvider = Stub(BuildCommencedTimeProvider) {
        getCurrentTime() >> 1234L
    }

    def cacheAccessCoordinator = new ArtifactCacheLockingAccessCoordinatorStub()
    def fileAccessTracker = Stub(FileAccessTracker)

    DefaultCachedExternalResourceIndex<String> index

    def setup() {
        index = new DefaultCachedExternalResourceIndex("index", BaseSerializerFactory.STRING_SERIALIZER, timeProvider, cacheAccessCoordinator, fileAccessTracker, tmp.testDirectory.toPath())
    }

    def "stores entry - lastModified = #lastModified"() {
        given:
        def key = "key"
        def artifactFile = tmp.createFile("artifact") << "content"

        when:
        index.store(key, artifactFile, new DefaultExternalResourceMetaData(new URI("abc"), lastModified, 100, contentType, etag, sha1, null, false))

        then:
        def cached = index.lookup(key)

        and:
        cached != null
        cached.cachedFile == artifactFile
        cached.cachedAt == 1234L
        cached.externalResourceMetaData != null
        cached.externalResourceMetaData.lastModified == lastModified
        cached.externalResourceMetaData.location == new URI("abc")
        cached.externalResourceMetaData.contentType == contentType
        cached.externalResourceMetaData.etag == etag
        cached.externalResourceMetaData.sha1 == sha1

        where:
        lastModified | contentType | etag   | sha1
        new Date()   | "something" | "etag" | TestHashCodes.hashCodeFrom(123456)
        null         | null        | null   | null
    }

    def "stores entry with no metadata"() {
        def artifactFile = tmp.createFile("artifact") << "content"

        when:
        index.store("key", artifactFile, null)

        then:
        def cached = index.lookup("key")

        and:
        cached != null
        cached.cachedFile == artifactFile
        cached.cachedAt == 1234L
        cached.externalResourceMetaData == null
    }

    def "stores missing entry"() {
        when:
        index.storeMissing("key")

        then:
        def cached = index.lookup("key")

        and:
        cached != null
        cached.cachedFile == null
        cached.cachedAt == 1234L
        cached.externalResourceMetaData == null
    }

}
