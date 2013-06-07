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

/**
 * By Szczepan Faber on 6/7/13
 */
class CacheAccessOperationsStackTest extends ConcurrentSpecification {

    def stack = new CacheAccessOperationsStack()

    def "maintains operations per thread"() {
        expect:
        start {
            assert !stack.isInCacheAction()
            stack.pushCacheAction("foo1")
            stack.pushCacheAction("foo2")
            assert stack.description == "foo2"
        }
        start {
            assert !stack.isInCacheAction()
            stack.pushCacheAction("bar1")
            stack.pushCacheAction("bar2")
            assert stack.description == "bar2"
        }
    }

    def "cannot access operations from a different thread"() {
        expect:
        start {
            stack.pushCacheAction("foo")
        }
        finished()

        when:
        start {
            stack.popCacheAction("foo")
        }
        finished()

        then:
        thrown(IllegalStateException)
    }

    def "manages reentrant long running operations"() {
        expect:
        start {
            assert !stack.maybeReentrantLongRunningOperation("long")
        }
        finished()
        start {
            assert !stack.maybeReentrantLongRunningOperation("long")
            stack.pushLongRunningOperation("long")
        }
        finished()
        start {
            assert stack.maybeReentrantLongRunningOperation("long2")
            assert stack.description == "long2"
        }
    }

    def "all long running operations must complete before "() {
        when:
        stack.pushLongRunningOperation("foo")
        stack.pushLongRunningOperation("foo2")

        then:
        stack.maybeReentrantLongRunningOperation("foo3")

        when:
        stack.popLongRunningOperation("foo3")
        stack.popLongRunningOperation("foo2")
        stack.popLongRunningOperation("foo")

        then:
        !stack.maybeReentrantLongRunningOperation("hey")
    }
}
