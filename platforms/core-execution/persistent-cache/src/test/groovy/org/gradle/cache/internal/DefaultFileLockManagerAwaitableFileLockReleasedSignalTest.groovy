/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.util.ConcurrentSpecification
import spock.lang.Subject

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class DefaultFileLockManagerAwaitableFileLockReleasedSignalTest extends ConcurrentSpecification {

    @Subject def signal = new DefaultFileLockManager.AwaitableFileLockReleasedSignal()

    def "can reuse signal"() {
        given:
        def signalCount = new AtomicInteger()

        when:
        start {
            while (signalCount.get() < 2) {
                def signaled = signal.await(10000)
                if (signaled) {
                    signalCount.incrementAndGet()
                }
            }
        }

        then:
        poll {
            assert signal.waiting
        }

        when:
        signal.trigger()

        then:
        poll {
            assert signalCount.get() == 1
            assert signal.waiting
        }

        when:
        signal.trigger()

        then:
        poll {
            assert signalCount.get() == 2
        }
    }

    def "can trigger signal without anyone waiting"() {
        when:
        signal.trigger()

        then:
        notThrown(Exception)
    }
}
