/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.process.internal.streams

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@Timeout(60)
class OutputStreamsForwarderTest extends Specification {

    def pending = []
    def executor = { Runnable task -> pending << task } as Executor
    def fireCount = new AtomicInteger()

    def process = Stub(Process) {
        getInputStream() >> new ByteArrayInputStream(new byte[0])
        getErrorStream() >> new ByteArrayInputStream(new byte[0])
    }

    OutputStreamsForwarder forwarder(boolean readErrorStream) {
        def forwarder = new OutputStreamsForwarder(
            new ByteArrayOutputStream(),
            new ByteArrayOutputStream(),
            readErrorStream
        )
        forwarder.connectStreams(process, "test process", executor)
        forwarder.start()
        forwarder
    }

    def "fires callback registered before streams finish once both pumps are done"() {
        def forwarder = forwarder(true)
        forwarder.whenStreamsFinished { fireCount.incrementAndGet() }

        when:
        pending[0].run()

        then:
        fireCount.get() == 0

        when:
        pending[1].run()

        then:
        fireCount.get() == 1
    }

    def "fires callback immediately when registered after streams finished"() {
        def forwarder = forwarder(true)
        pending.each { it.run() }

        when:
        forwarder.whenStreamsFinished { fireCount.incrementAndGet() }

        then:
        fireCount.get() == 1
    }

    def "fires each registered callback exactly once"() {
        def forwarder = forwarder(true)
        pending.each { it.run() }

        when:
        forwarder.whenStreamsFinished { fireCount.incrementAndGet() }
        forwarder.whenStreamsFinished { fireCount.incrementAndGet() }

        then:
        fireCount.get() == 2
    }

    def "fires callback when only standard output is read"() {
        def forwarder = forwarder(false)
        forwarder.whenStreamsFinished { fireCount.incrementAndGet() }

        when:
        pending[0].run()

        then:
        pending.size() == 1
        fireCount.get() == 1
    }

    def "finishing streams without a registered callback is a no-op"() {
        forwarder(true)

        when:
        pending.each { it.run() }

        then:
        noExceptionThrown()
        fireCount.get() == 0
    }
}
