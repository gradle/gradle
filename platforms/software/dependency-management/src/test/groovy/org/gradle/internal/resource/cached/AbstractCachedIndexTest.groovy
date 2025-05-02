/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER

class AbstractCachedIndexTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    static final CACHE_NAME = "my-cache"
    def cacheAccessCoordinator = new ArtifactCacheLockingAccessCoordinatorStub()
    def fileAccessTracker = Mock(FileAccessTracker)
    def valueSerializer = new Serializer<CachedItem>() {
        @Override
        CachedItem read(Decoder decoder) throws EOFException, Exception {
            def filename = decoder.readString()
            def file = filename.empty ? null : new File(filename)
            def missing = decoder.readBoolean()
            return cachedItem(file, missing)
        }

        @Override
        void write(Encoder encoder, CachedItem value) throws Exception {
            encoder.writeString(value.cachedFile?.absolutePath ?: "")
            encoder.writeBoolean(value.missing)
        }
    }

    @Subject AbstractCachedIndex<String, CachedItem> cachedIndex = new AbstractCachedIndex(CACHE_NAME, STRING_SERIALIZER, valueSerializer, cacheAccessCoordinator, fileAccessTracker) {}

    def "tracks access to looked up files"() {
        given:
        def cachedFile = temporaryFolder.createFile("foo.txt")

        when:
        cachedIndex.storeInternal("foo", cachedItem(cachedFile))
        def item = cachedIndex.lookup("foo")

        then:
        item.cachedFile == cachedFile
        1 * fileAccessTracker.markAccessed(cachedFile)
    }

    def "clears entries for deleted files"() {
        given:
        def cachedFile = temporaryFolder.file("foo.txt")

        when:
        cachedIndex.storeInternal("foo", cachedItem(cachedFile))
        def item = cachedIndex.lookup("foo")

        then:
        item == null
        cacheAccessCoordinator.getCache(CACHE_NAME).getIfPresent("foo") == null
        0 * fileAccessTracker.markAccessed(_)
    }

    def "returns missing items"() {
        given:
        def missingItem = cachedItem(null, true)

        when:
        cachedIndex.storeInternal("foo", missingItem)
        def item = cachedIndex.lookup("foo")

        then:
        item.missing
        item.cachedFile == null
        0 * fileAccessTracker.markAccessed(_)
    }

    def "returns null for unknown keys"() {
        when:
        def item = cachedIndex.lookup("foo")

        then:
        item == null
        0 * fileAccessTracker.markAccessed(_)
    }

    private CachedItem cachedItem(file, missing=false) {
        new CachedItem() {
            @Override
            boolean isMissing() {
                return missing
            }

            @Override
            File getCachedFile() {
                return file
            }

            @Override
            long getCachedAt() {
                return 0
            }
        }
    }
}
