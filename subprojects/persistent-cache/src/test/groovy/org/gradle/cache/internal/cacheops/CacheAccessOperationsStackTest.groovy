/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.cache.internal.cacheops

import org.gradle.util.ConcurrentSpecification

class CacheAccessOperationsStackTest extends ConcurrentSpecification {

    def stack = new CacheAccessOperationsStack()

    def "maintains operations per thread #count"() {
        expect:
        start {
            assert !stack.inCacheAction
            stack.pushCacheAction()
            stack.pushCacheAction()
            assert stack.inCacheAction
        }
        start {
            assert !stack.inCacheAction
            stack.pushCacheAction()
            stack.pushCacheAction()
            assert stack.inCacheAction
            stack.popCacheAction()
            stack.popCacheAction()
            assert !stack.inCacheAction
        }
        finished()
        where:
        count << (1..1000)
    }
}
