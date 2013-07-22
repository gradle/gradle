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

    def "provides no description initially"() {
        when:
        stack.description
        then:
        thrown(IllegalStateException)
    }

    def "manages long running operations"() {
        when:
        stack.pushLongRunningOperation("long")
        then:
        stack.description == "long"

        when:
        stack.pushLongRunningOperation("long2")
        then:
        stack.description == "long2"

        when:
        stack.popLongRunningOperation("long2")
        then:
        stack.description == "long"

        when:
        stack.popLongRunningOperation("long")
        and:
        stack.description
        then:
        thrown(IllegalStateException)
    }

    def "manages cache actions"() {
        when:
        stack.pushCacheAction("foo")
        then:
        stack.description == "foo"

        when:
        stack.pushCacheAction("foo2")
        then:
        stack.description == "foo2"

        when:
        stack.popCacheAction("foo2")
        then:
        stack.description == "foo"

        when:
        stack.popCacheAction("foo")
        and:
        stack.description
        then:
        thrown(IllegalStateException)
    }

    def "prevents popping latest operation if the name does not match"() {
        stack.pushCacheAction("foo")

        when:
        stack.popCacheAction("foo2")
        then:
        thrown(IllegalStateException)
    }

    def "prevents popping latest operation if the kind does not match"() {
        stack.pushCacheAction("foo")

        when:
        stack.popLongRunningOperation("foo")
        then:
        thrown(IllegalStateException)
    }

    def "knows the kind of current cache operation"() {
        assert !stack.isInCacheAction()

        when:
        stack.pushLongRunningOperation("long")
        then:
        !stack.inCacheAction
        stack.inLongRunningOperation

        when:
        stack.pushCacheAction("cache")
        then:
        stack.inCacheAction
        !stack.inLongRunningOperation

        when:
        stack.pushCacheAction("cache2")
        then:
        stack.inCacheAction
        !stack.inLongRunningOperation

        when:
        stack.popCacheAction("cache2")
        stack.popCacheAction("cache")
        then:
        !stack.inCacheAction
        stack.inLongRunningOperation
    }
}
