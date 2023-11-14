/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.controller

import org.gradle.api.Action
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.DefaultBuildCacheKey
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration
import org.gradle.caching.internal.origin.OriginMetadataFactory
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker
import org.gradle.caching.local.internal.LocalBuildCacheService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.time.Duration
import java.util.function.Consumer

class DefaultBuildCacheControllerTest extends Specification {

    def key = new DefaultBuildCacheKey(TestHashCodes.hashCodeFrom(12345678))

    CacheableEntity cacheableEntity = Stub(CacheableEntity) {
        identity >> ":test"
        type >> CacheableEntity
    }
    Duration executionTime = Duration.ofMillis(123)
    Map<String, FileSystemSnapshot> snapshots = [:]

    def local = Mock(Local) {
        withTempFile(_ as HashCode, _ as Consumer) >> { key, action ->
            action.accept(tmpDir.file("file"))
        }
    }
    def localPush = true
    def remote = Mock(BuildCacheService)
    def remotePush = true
    FileSystemAccess fileSystemAccess = Stub(FileSystemAccess)
    BuildCacheEntryPacker packer = Stub(BuildCacheEntryPacker)
    OriginMetadataFactory originMetadataFactory = Stub(OriginMetadataFactory)
    StringInterner stringInterner = Stub(StringInterner)

    def operations = new TestBuildOperationExecutor()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    interface Local extends BuildCacheService, LocalBuildCacheService {}

    BuildCacheController getController(boolean disableRemoteOnError = true) {
        new DefaultBuildCacheController(
            new BuildCacheServicesConfiguration(
                local,
                localPush,
                remote,
                remotePush
            ),
            operations,
            TestFiles.tmpDirTemporaryFileProvider(tmpDir.testDirectory),
            false,
            false,
            disableRemoteOnError,
            fileSystemAccess,
            packer,
            originMetadataFactory,
            stringInterner
        )
    }

    def "does suppress exceptions from load"() {
        given:
        1 * remote.load(key, _) >> { throw new RuntimeException() }

        when:
        controller.load(key, cacheableEntity)

        then:
        1 * local.loadLocally(key, _)
        0 * local.storeLocally(key, _)
    }

    def "does suppress exceptions from store"() {
        given:
        1 * remote.store(key, _) >> { throw new RuntimeException() }

        when:
        controller.store(key, cacheableEntity, snapshots, executionTime)

        then:
        noExceptionThrown()

        and:
        1 * local.storeLocally(key, _)
    }

    def "does not store to local if local push is disabled"() {
        given:
        localPush = false

        when:
        controller.store(key, cacheableEntity, snapshots, executionTime)

        then:
        0 * local.storeLocally(key, _)
    }

    def "does not store to local if no local"() {
        given:
        local = null

        when:
        controller.store(key, cacheableEntity, snapshots, executionTime)

        then:
        0 * local.store(key, _)
    }

    def "local load does not stores to local"() {
        given:
        1 * local.loadLocally(key, _) >> { BuildCacheKey key, Action<File> action ->
            def file = tmpDir.file("file")
            file.text = "alma"
            action.execute(file)
        }

        when:
        controller.load(key, cacheableEntity)

        then:
        0 * local.storeLocally(key, _)
    }

    def "remote load also stores to local"() {
        given:
        1 * local.loadLocally(key, _) // miss
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(key, cacheableEntity)

        then:
        1 * local.storeLocally(key, _)
    }

    def "remote load does not store to local if local is disabled"() {
        given:
        local = null
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(key, cacheableEntity)

        then:
        noExceptionThrown()
    }

    def "remote load does not store to local if local push is disabled"() {
        given:
        localPush = false
        1 * local.loadLocally(key, _) // miss
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(key, cacheableEntity)

        then:
        0 * local.storeLocally(key, _)
    }

    def "stops calling through after read error"() {
        local = null

        when:
        def controller = getController()
        controller.load(key, cacheableEntity)
        controller.load(key, cacheableEntity)
        controller.store(key, cacheableEntity, snapshots, executionTime)

        then:
        1 * remote.load(key, _) >> { throw new RuntimeException() }
        0 * remote.load(key, _)
        0 * remote.store(key, _)
    }

    def "stops calling through after write error"() {
        local = null

        when:
        def controller = getController()
        controller.store(key, cacheableEntity, snapshots, executionTime)
        controller.store(key, cacheableEntity, snapshots, executionTime)
        controller.load(key, cacheableEntity)

        then:
        1 * remote.store(key, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            throw new RuntimeException()
        }
        0 * remote.load(key, _)
        0 * remote.store(key, _)
    }

    def "does not stop calling remote on read error if disable-on-error disabled"() {
        local = null

        when:
        def controller = getController(false)
        controller.load(key, cacheableEntity)
        controller.load(key, cacheableEntity)
        controller.store(key, cacheableEntity, snapshots, executionTime)

        then:
        1 * remote.load(key, _) >> { throw new RuntimeException() }
        1 * remote.load(key, _)
        1 * remote.store(key, _)
    }

    def "does not stop calling remote on write error if disable-on-error disabled"() {
        local = null

        when:
        def controller = getController(false)
        controller.store(key, cacheableEntity, snapshots, executionTime)
        controller.store(key, cacheableEntity, snapshots, executionTime)
        controller.load(key, cacheableEntity)

        then:
        1 * remote.store(key, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            throw new RuntimeException()
        }
        1 * remote.load(key, _)
        1 * remote.store(key, _)
    }

    def "close only closes once"() {
        when:
        def controller = getController()
        controller.close()
        controller.close()
        controller.close()

        then:
        1 * local.close()
        1 * remote.close()
    }
}
