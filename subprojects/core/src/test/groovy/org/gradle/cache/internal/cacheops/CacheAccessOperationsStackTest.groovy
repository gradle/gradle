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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    def "when all long running operations complete the next operation is no longer reentrant"() {
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

    def "if any thread is in cache the next long running operation is not reentrant"() {
        when:
        stack.pushLongRunningOperation("long")
        start { stack.pushCacheAction("cache") }
        finished()

        then:
        !stack.maybeReentrantLongRunningOperation("long2")
    }

    def "long running operation is reentrant if previous long running operation belongs to a different thread"() {
        when:
        start {
            stack.pushLongRunningOperation("a")
        }
        finished()
        then:
        stack.maybeReentrantLongRunningOperation("b")
    }

    def "long running operations in separate threads can interleave"() {
        when:
        //Here's the scenario:
        //Thread 1: pushes a
        //Thread 2: pushes b
        //Thread 1: pops a
        //Thread 2: pops b

        def latch1 = new CountDownLatch(1)
        def latch2 = new CountDownLatch(1)
        start {
            stack.pushLongRunningOperation("a")
            latch1.await(1, TimeUnit.SECONDS)
            stack.popLongRunningOperation("a")
            latch2.countDown()
        }
        start {
            stack.pushLongRunningOperation("b")
            latch1.countDown()
            latch2.await(1, TimeUnit.SECONDS)
            stack.popLongRunningOperation("b")
        }
        finished()

        then:
        noExceptionThrown()
    }
}
