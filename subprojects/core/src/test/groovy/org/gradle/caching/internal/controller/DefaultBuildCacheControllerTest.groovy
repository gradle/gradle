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
import org.gradle.api.internal.file.DefaultTemporaryFileProvider
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule

import static org.gradle.caching.internal.controller.DefaultBuildCacheController.MAX_ERRORS

class DefaultBuildCacheControllerTest extends Specification {

    def key = Mock(BuildCacheKey)
    def local = Mock(BuildCacheService)
    def remote = Mock(BuildCacheService)
    def storeOp = Stub(BuildCacheStoreOp) {
        getKey() >> key
    }
    def loadOp = Stub(BuildCacheLoadOp) {
        getKey() >> key
    }

    def operations = new TestBuildOperationExecutor()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def controller = new DefaultBuildCacheController(
        new BuildCacheServiceRef(local, true),
        new BuildCacheServiceRef(remote, true),
        operations,
        new DefaultTemporaryFileProvider({ tmpDir.file("dir") }),
        false
    )

    def "does not suppress exceptions from load"() {
        given:
        1 * local.load(key, _) >> { throw new RuntimeException() }

        when:
        controller.load(loadOp)

        then:
        def exception = thrown(GradleException)
        exception.message.contains key.toString()
    }

    def "does suppress exceptions from store"() {
        given:
        1 * local.store(key, _) >> { throw new RuntimeException() }
        1 * remote.store(key, _) >> { throw new RuntimeException() }

        when:
        controller.store(storeOp)

        then:
        noExceptionThrown()
    }

    def "stops calling through after defined number of read errors"() {
        when:
        (MAX_ERRORS + 1).times {
            controller.load(loadOp)
        }
        controller.store(storeOp)

        then:
        MAX_ERRORS * local.load(key, _) >> { throw new BuildCacheException("Error") }
        MAX_ERRORS * remote.load(key, _)
        1 * remote.load(key, _)
        0 * local.store(_, _)
        1 * remote.store(_, _)
    }

    def "stops calling through after defined number of write errors"() {
        when:
        (MAX_ERRORS + 1).times {
            controller.store(storeOp)
        }
        controller.load(loadOp)

        then:
        MAX_ERRORS * local.store(key, _) >> { throw new BuildCacheException("Error") }
        MAX_ERRORS * remote.store(key, _)
        1 * remote.store(key, _)
        0 * local.load(_, _)
        1 * remote.load(_, _)
    }

    def "close only closes once"() {
        when:
        controller.close()
        controller.close()
        controller.close()

        then:
        1 * local.close()
        1 * remote.close()
    }
}
