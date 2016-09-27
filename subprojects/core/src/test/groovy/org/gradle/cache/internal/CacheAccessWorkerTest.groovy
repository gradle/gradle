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

import org.gradle.cache.CacheAccess
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class CacheAccessWorkerTest extends ConcurrentSpec {
    def cacheAccess

    def setup() {
        cacheAccess = Stub(CacheAccess) {
            useCache(_, _) >> { String operationDisplayName, Runnable action -> action.run() }
        }
    }

    def "flushes existing operations when stop is called"() {
        given:
        def counter = 0
        def action = {
            Thread.sleep(200L)
            counter++
        } as Runnable
        def cacheAccessWorker = new CacheAccessWorker(cacheAccess, 512, 200L, 10000L)
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)
        cacheAccessWorker.enqueue(action)
        start(cacheAccessWorker)
        Thread.sleep(200L) // wait that CacheAccessWorker sets it's running flag

        when:
        cacheAccessWorker.stop()

        then:
        noExceptionThrown()
        counter == 3
    }
}
