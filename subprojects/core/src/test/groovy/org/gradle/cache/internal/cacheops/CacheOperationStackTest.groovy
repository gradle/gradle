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

import spock.lang.Specification

class CacheOperationStackTest extends Specification {

    def stack = new CacheOperationStack()

    def "prevents popping latest operation if the kind does not match"() {
        stack.pushCacheAction()

        when:
        stack.popLongRunningOperation()
        then:
        thrown(IllegalStateException)
    }

    def "knows the kind of current cache operation"() {
        assert !stack.isInCacheAction()

        when:
        stack.pushLongRunningOperation()
        then:
        !stack.inCacheAction
        stack.inLongRunningOperation

        when:
        stack.pushCacheAction()
        then:
        stack.inCacheAction
        !stack.inLongRunningOperation

        when:
        stack.pushCacheAction()
        then:
        stack.inCacheAction
        !stack.inLongRunningOperation

        when:
        stack.popCacheAction()
        stack.popCacheAction()
        then:
        !stack.inCacheAction
        stack.inLongRunningOperation
    }
}
