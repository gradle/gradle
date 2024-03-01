/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.cache.internal


import org.gradle.cache.ExclusiveCacheAccessCoordinator
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class ExclusiveCacheAccessingWorkerTest extends ConcurrentSpec {
    ExclusiveCacheAccessCoordinator cacheAccess
    ExclusiveCacheAccessingWorker cacheAccessWorker

    def setup() {
        cacheAccess = Stub(ExclusiveCacheAccessCoordinator) {
            useCache(_) >> { Runnable action -> action.run() }
        }
        cacheAccessWorker = new ExclusiveCacheAccessingWorker("<cache>", cacheAccess)
    }

    def "read runs after queued writes are processed"() {
        given:
        def counter = 0
        start(cacheAccessWorker)

        when:
        cacheAccessWorker.enqueue { ++counter }
        cacheAccessWorker.enqueue { ++counter }
        def result = cacheAccessWorker.read { counter }

        then:
        result == 2

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "read propagates failure"() {
        given:
        def failure = new RuntimeException()
        start(cacheAccessWorker)

        when:
        cacheAccessWorker.read { throw failure }

        then:
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "read completes after failed update"() {
        given:
        def failure = new RuntimeException()
        start(cacheAccessWorker)

        when:
        cacheAccessWorker.enqueue { throw failure }
        def result = cacheAccessWorker.read { 2 }

        then:
        result == 2

        when:
        cacheAccessWorker.stop()

        then:
        def e = thrown(RuntimeException)
        e == failure
    }

    def "continues on failed operations collecting only the first failure"() {
        given:
        def counter = 0
        def failure = new RuntimeException()
        start(cacheAccessWorker)

        when:
        cacheAccessWorker.enqueue { throw failure }
        cacheAccessWorker.enqueue { throw new RuntimeException() }
        cacheAccessWorker.enqueue { counter++ }
        cacheAccessWorker.flush()

        then:
        counter == 1
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "stop waits for queued actions to complete"() {
        given:
        def counter = 0
        def action = {
            thread.block()
            counter++
        }
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)

        when:
        start(cacheAccessWorker)
        cacheAccessWorker.stop()

        then:
        counter == 3
    }

    def "flush waits for queued actions to complete"() {
        given:
        def counter = 0
        def action = {
            thread.block()
            counter++
        }
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)

        when:
        start(cacheAccessWorker)
        cacheAccessWorker.flush()

        then:
        counter == 3

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "flush rethrows action failure"() {
        def failure = new RuntimeException()
        cacheAccessWorker.enqueue { throw failure }

        when:
        start(cacheAccessWorker)
        cacheAccessWorker.flush()

        then:
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "stop rethrows action failure that occurs while waiting"() {
        def failure = new RuntimeException()
        cacheAccessWorker.enqueue {
            instant.waiting
            thread.block()
        }
        cacheAccessWorker.enqueue {
            throw failure
        }

        when:
        start(cacheAccessWorker)
        async {
            thread.blockUntil.waiting
            cacheAccessWorker.stop()
        }

        then:
        def e = thrown(RuntimeException)
        e == failure

        cleanup:
        cacheAccessWorker?.stop()
    }

    def "stop rethrows action failure"() {
        def failure = new RuntimeException()
        cacheAccessWorker.enqueue { throw failure }

        when:
        start(cacheAccessWorker)
        cacheAccessWorker.stop()

        then:
        def e = thrown(RuntimeException)
        e == failure
    }
}
