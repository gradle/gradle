/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configuration.internal

import spock.lang.Specification

import java.util.concurrent.CountDownLatch

class DefaultDynamicCallContextTrackerTest extends Specification {
    def tracker = new DefaultDynamicCallContextTracker()

    def "invokes callbacks in the right order with the right arguments"() {
        given:
        def log = []
        tracker.onEnter { log += ["enter", it] }
        tracker.onLeave { log += ["leave", it] }

        when:
        def entryPoint1 = new Object()
        def entryPoint2 = new Object()
        tracker.enterDynamicCall(entryPoint1)
        tracker.enterDynamicCall(entryPoint2)
        tracker.leaveDynamicCall(entryPoint2)
        tracker.leaveDynamicCall(entryPoint1)

        then:
        log == ["enter", entryPoint1, "enter", entryPoint2, "leave", entryPoint2, "leave", entryPoint1]
    }

    def "throws exception on mismatched calls"() {
        when:
        tracker.enterDynamicCall(new Object())
        tracker.leaveDynamicCall(new Object())

        then:
        thrown IllegalStateException
    }

    def "tracks calls per thread independently"() {
        given:
        // work1.enter -> work2.enter -> work1.leave -> work2.leave
        def latch1 = new CountDownLatch(1)
        def latch2 = new CountDownLatch(1)
        def latch3 = new CountDownLatch(1)
        Runnable work1 = {
            tracker.enterDynamicCall(latch1)
            latch1.countDown()
            latch2.await()
            tracker.leaveDynamicCall(latch1)
            latch3.countDown()
        }
        Runnable work2 = {
            latch1.await()
            tracker.enterDynamicCall(latch2)
            latch2.countDown()
            latch3.await()
            tracker.leaveDynamicCall(latch2)
        }
        def threads = [new Thread(work1), new Thread(work2)]

        when:
        def failure = null
        threads.forEach { it.uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            void uncaughtException(Thread t, Throwable e) {
                failure = e
            }
        }}
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        then:
        failure == null
    }
}
