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

package org.gradle.internal.filewatch

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SingleFirePendingChangesListenerTest extends Specification {
    def delegateCalled = new AtomicInteger()
    // Can't use Spock Mocks in parallel, so we use an actual implementation here.
    def delegate = new PendingChangesListener() {
        @Override
        void onPendingChanges() {
            delegateCalled.incrementAndGet()
        }
    }
    int numberOfConcurrentThreads = 20
    def executer = Executors.newFixedThreadPool(numberOfConcurrentThreads)

    def "propagates changes only once"() {
        def threadsReadyLatch = new CountDownLatch(numberOfConcurrentThreads)
        def startChangesLatch = new CountDownLatch(1)
        def threadsFinishedLatch = new CountDownLatch(numberOfConcurrentThreads)
        def singleFireListener = new SingleFirePendingChangesListener(delegate)
        (1..numberOfConcurrentThreads).each {
            executer.submit {
                threadsReadyLatch.countDown()
                startChangesLatch.await()
                singleFireListener.onPendingChanges()
                threadsFinishedLatch.countDown()
            }
        }

        when:
        startChangesLatch.countDown()
        threadsFinishedLatch.await()
        then:
        delegateCalled.get() == 1
    }

    def cleanup() {
        executer.shutdownNow()
    }
}
