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

package org.gradle.test.fixtures.concurrent

import java.util.concurrent.atomic.AtomicBoolean

class ContentionGateTest extends ConcurrentSpec {

    def gate = new ContentionGate()
    def lock = new Object()

    def "releases the first thread when another one waits for its lock"() {
        def value = null
        def results = [].asSynchronized()
        def compute = {
            synchronized (lock) {
                if (value == null) {
                    value = gate.pass { "value" }
                }
                value
            }
        }

        when:
        async {
            start { results << compute() }
            start { results << compute() }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 1
        results == ["value", "value"]
    }

    def "records threads that pass one at a time"() {
        when:
        async {
            start { synchronized (lock) { gate.pass {} } }
            start { synchronized (lock) { gate.pass {} } }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 2
        gate.maxConcurrentEntries == 1
    }

    def "releases the first thread when another one enters as well"() {
        when:
        async {
            start { gate.pass {} }
            start { gate.pass {} }
            gate.releaseWhenContended()
        }

        then:
        gate.entries == 2
        gate.maxConcurrentEntries == 2
    }

    def "releases the first thread when a custom condition holds"() {
        def contenderArrived = new AtomicBoolean()

        when:
        async {
            start { gate.pass {} }
            start { contenderArrived.set(true) }
            gate.releaseWhen { contenderArrived.get() }
        }

        then:
        gate.entries == 1
    }

    def "fails and releases the first thread when no other thread contends"() {
        def impatientGate = new ContentionGate(1)
        def result = null

        when:
        async {
            start { result = impatientGate.pass { "value" } }
            impatientGate.releaseWhenContended()
        }

        then:
        def e = thrown(AssertionError)
        e.message.contains("No thread contended with the first one")
        result == "value"
    }

    def "fails the first thread when it is never released"() {
        def impatientGate = new ContentionGate(1)

        when:
        async {
            start { impatientGate.pass {} }
        }

        then:
        def e = thrown(AssertionError)
        e.message == "The first thread was never released"
    }

    def "cannot release the first thread twice"() {
        when:
        async {
            start { gate.pass {} }
            start { gate.pass {} }
            gate.releaseWhenContended()
        }
        gate.releaseWhenContended()

        then:
        def e = thrown(IllegalStateException)
        e.message == "The first thread was already released"
    }
}
