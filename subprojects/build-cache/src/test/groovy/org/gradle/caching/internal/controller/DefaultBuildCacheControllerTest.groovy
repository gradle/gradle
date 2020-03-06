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
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration
import org.gradle.caching.local.internal.LocalBuildCacheService
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification
import org.junit.Rule

class DefaultBuildCacheControllerTest extends Specification {

    def key = Mock(BuildCacheKey) {
        getHashCode() >> "key"
        toString() >> "key"
    }

    def local = Mock(Local) {
        withTempFile(_ as BuildCacheKey, _ as Action) >> { key, action ->
            action.execute(tmpDir.file("file"))
        }
    }
    def localPush = true
    def remote = Mock(BuildCacheService)
    def remotePush = true
    def loadmetadata = Mock(Object)

    def storeCommand = Stub(BuildCacheStoreCommand) {
        getKey() >> key
        store(_ as OutputStream) >> { OutputStream output ->
            output.close()
            new BuildCacheStoreCommand.Result() {
                @Override
                long getArtifactEntryCount() {
                    return 0
                }
            }
        }
    }

    def loadCommand = Stub(BuildCacheLoadCommand) {
        getKey() >> key
        load(_ as InputStream) >> { InputStream input ->
            input.close()
            new BuildCacheLoadCommand.Result() {
                @Override
                long getArtifactEntryCount() {
                    return 0
                }

                @Override
                Object getMetadata() {
                    return loadmetadata
                }
            }
        }
    }

    def operations = new TestBuildOperationExecutor()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    interface Local extends BuildCacheService, LocalBuildCacheService {}

    BuildCacheController getController() {
        new DefaultBuildCacheController(
            new BuildCacheServicesConfiguration(
                local,
                localPush,
                remote,
                remotePush
            ),
            operations,
            tmpDir.file("dir"),
            false,
            false
        )
    }

    def "does suppress exceptions from load"() {
        given:
        1 * remote.load(key, _) >> { throw new RuntimeException() }

        when:
        controller.load(loadCommand)

        then:
        1 * local.loadLocally(key, _)
        0 * local.storeLocally(key, _)
    }

    def "does suppress exceptions from store"() {
        given:
        1 * remote.store(key, _) >> { throw new RuntimeException() }

        when:
        controller.store(storeCommand)

        then:
        noExceptionThrown()

        and:
        1 * local.storeLocally(key, _)
    }

    def "does not store to local if local push is disabled"() {
        given:
        localPush = false

        when:
        controller.store(storeCommand)

        then:
        0 * local.storeLocally(key, _)
    }

    def "does not store to local if no local"() {
        given:
        local = null

        when:
        controller.store(storeCommand)

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
        controller.load(loadCommand)

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
        controller.load(loadCommand)

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
        controller.load(loadCommand)

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
        controller.load(loadCommand)

        then:
        0 * local.storeLocally(key, _)
    }

    def "stops calling through after read error"() {
        local = null

        when:
        def controller = getController()
        controller.load(loadCommand)
        controller.load(loadCommand)
        controller.store(storeCommand)

        then:
        1 * remote.load(key, _) >> { throw new RuntimeException() }
        0 * remote.load(key, _)
        0 * remote.store(key, _)
    }

    def "stops calling through after write error"() {
        local = null

        when:
        def controller = getController()
        controller.store(storeCommand)
        controller.store(storeCommand)
        controller.load(loadCommand)

        then:
        1 * remote.store(key, _) >> { BuildCacheKey key, BuildCacheEntryWriter writer ->
            throw new RuntimeException()
        }
        0 * remote.load(key, _)
        0 * remote.store(key, _)
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
