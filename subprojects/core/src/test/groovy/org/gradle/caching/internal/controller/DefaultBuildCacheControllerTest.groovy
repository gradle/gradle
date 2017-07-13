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

import org.gradle.api.GradleException
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration
import org.gradle.caching.local.internal.LocalBuildCacheService
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule

@LeaksFileHandles
class DefaultBuildCacheControllerTest extends Specification {

    def key = Mock(BuildCacheKey) {
        getHashCode() >> "key"
    }

    def local = Mock(Local) {
        allocateTempFile(_, _) >> { key, action ->
            action.execute(tmpDir.file("file"))
        }
    }
    def localPush = true
    def remote = Mock(BuildCacheService)
    def remotePush = true

    BuildCacheService legacyLocal = null

    def storeCommand = Stub(BuildCacheStoreCommand) {
        getKey() >> key
    }
    def loadCommand = Stub(BuildCacheLoadCommand) {
        getKey() >> key
    }

    def operations = new TestBuildOperationExecutor()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    interface Local extends BuildCacheService, LocalBuildCacheService {}

    BuildCacheController getController() {
        new DefaultBuildCacheController(
            new BuildCacheServicesConfiguration(
                legacyLocal ?: local, localPush,
                remote, remotePush
            ),
            operations,
            tmpDir.file("dir"),
            false
        )
    }

    def "does not suppress exceptions from load"() {
        given:
        1 * remote.load(key, _) >> { throw new RuntimeException() }

        when:
        controller.load(loadCommand)

        then:
        def exception = thrown(GradleException)
        exception.message.contains key.toString()

        and:
        1 * local.load(key, _)
        0 * local.store(key, _)
    }

    def "does suppress exceptions from store"() {
        given:
        1 * remote.store(key, _) >> { throw new RuntimeException() }

        when:
        controller.store(storeCommand)

        then:
        noExceptionThrown()

        and:
        1 * local.store(key, _)
    }

    def "does not store to local if local push is disabled"() {
        given:
        localPush = false

        when:
        controller.store(storeCommand)

        then:
        0 * local.store(key, _)
    }

    def "does not store to local if no local"() {
        given:
        local = null

        when:
        controller.store(storeCommand)

        then:
        0 * local.store(key, _)
    }

    def "remote load also stores to local"() {
        given:
        1 * local.load(key, _) // miss
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(loadCommand)

        then:
        1 * local.store(key, _)
    }

    def "remote load does not store to local if local is disabled"() {
        given:
        local = null
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(loadCommand)

        then:
        noExceptionThrown()
    }

    def "remote load does not store to local if local push is disabled"() {
        given:
        localPush = false
        1 * local.load(key, _) // miss
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(loadCommand)

        then:
        0 * local.store(key, _)
    }

    def "stops calling through after defined number of read errors"() {
        when:
        def controller = getController()
        controller.load(loadCommand)
        controller.load(loadCommand)
        controller.store(storeCommand)

        then:
        1 * remote.load(key, _)
        1 * remote.load(key, _)
        1 * remote.store(_, _)
    }

    def "stops calling through after defined number of write errors"() {
        when:
        def controller = getController()
        controller.store(storeCommand)
        controller.store(storeCommand)
        controller.load(loadCommand)

        then:
        1 * remote.store(key, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            writer.writeTo(NullOutputStream.INSTANCE)
        }
        1 * remote.store(key, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            writer.writeTo(NullOutputStream.INSTANCE)
        }
        1 * remote.load(_, _)
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

    def "does not store to local cache if using legacy local service"() {
        given:
        legacyLocal = Mock(BuildCacheService)
        1 * legacyLocal.load(key, _) // miss
        1 * remote.load(key, _) >> { BuildCacheKey key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(loadCommand)

        then:
        0 * legacyLocal.store(key, _)
    }

    def "legacy local loads do not emit ops"() {
        given:
        legacyLocal = Mock(BuildCacheService)
        remote = null
        1 * legacyLocal.load(key, _) // miss
        1 * legacyLocal.load(key, _) >> { key, BuildCacheEntryReader reader ->
            reader.readFrom(new ByteArrayInputStream("foo".bytes))
            true
        }

        when:
        controller.load(loadCommand)
        controller.load(loadCommand)

        then:
        with(operations.log.descriptors) {
            size() == 1
            get(0).displayName ==~ "Unpack $key .+"
        }
    }

    def "legacy local stores do not emit ops"() {
        given:
        legacyLocal = Mock(BuildCacheService)
        remote = null
        1 * legacyLocal.store(key, _) >> { key, BuildCacheEntryWriter writer ->
            writer.writeTo(new ByteArrayOutputStream())
        }

        when:
        controller.store(storeCommand)

        then:
        with(operations.log.descriptors) {
            size() == 1
            get(0).displayName ==~ "Pack $key .+"
        }
    }

    def "legacy errors do not emit ops"() {
        given:
        legacyLocal = Mock(BuildCacheService)
        remote = null
        1 * legacyLocal.store(key, _) >> { key, BuildCacheEntryWriter writer ->
            new Exception("!")
        }

        when:
        controller.store(storeCommand)

        then:
        with(operations.log.descriptors) {
            size() == 1
            get(0).displayName ==~ "Pack $key .+"
        }
    }

}
