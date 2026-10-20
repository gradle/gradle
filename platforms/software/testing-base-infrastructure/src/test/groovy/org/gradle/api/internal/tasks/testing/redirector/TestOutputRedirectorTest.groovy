/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.redirector

import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.internal.time.FixedClock
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TestOutputRedirectorTest extends Specification {

    TestResultProcessor processor = Mock(TestResultProcessor)
    StandardOutputRedirector redir = Mock(StandardOutputRedirector)

    @Subject redirector = new TestOutputRedirector(new FixedClock(123), processor, redir)

    def "starts redirecting output and error"() {
        when:
        redirector.testStarted("1")
        redirector.startRedirecting()

        then:
        1 * redir.redirectStandardErrorTo({ it.dest == TestOutputEvent.Destination.StdErr })
        1 * redir.redirectStandardOutputTo({ it.dest == TestOutputEvent.Destination.StdOut })

        then:
        1 * redir.start()
        0 * _
    }

    def "disallows starting redirecting if test owner not provided"() {
        when: redirector.startRedirecting()
        then: thrown(AssertionError)
    }

    def "passes output events"() {
        redirector.testStarted("5")

        when: redirector.errForwarder.onOutput("ala")

        then:
        1 * processor.output("5", { DefaultTestOutputEvent e ->
            e.destination == TestOutputEvent.Destination.StdErr &&
                e.message == "ala"
        })
        0 * _
    }

    def "attributes output to the test started on the writing thread"() {
        redirector.testStarted("root")
        onThread("a") { redirector.testStarted("a") }
        onThread("b") { redirector.testStarted("b") }

        when:
        onThread("a") { redirector.outForwarder.onOutput("from a") }
        onThread("b") { redirector.outForwarder.onOutput("from b") }
        redirector.outForwarder.onOutput("from root")

        then:
        1 * processor.output("a", { it.message == "from a" })
        1 * processor.output("b", { it.message == "from b" })
        1 * processor.output("root", { it.message == "from root" })
        0 * _
    }

    def "attributes output to the parent after a test completes on the writing thread"() {
        redirector.testStarted("root")
        redirector.testStarted("class")
        onThread("a") {
            redirector.testStarted("method")
            redirector.testCompleted("method", "class")
        }

        when:
        onThread("a") { redirector.outForwarder.onOutput("after method") }

        then:
        1 * processor.output("class", { it.message == "after method" })
        0 * _
    }

    def "attributes output from a thread without an active test to the last active owner"() {
        redirector.testStarted("root")
        onThread("a") { redirector.testStarted("a") }
        redirector.testStarted("b")
        redirector.testCompleted("a", "root")

        when:
        onThread("a") { redirector.outForwarder.onOutput("stale a") }
        onThread("other") { redirector.outForwarder.onOutput("no test") }

        then:
        2 * processor.output("root", _)
        0 * _
    }

    private final Map<String, ExecutorService> threads = [:]

    private void onThread(String name, Runnable action) {
        threads.computeIfAbsent(name) { Executors.newSingleThreadExecutor() }.submit(action).get()
    }

    def cleanup() {
        threads.values()*.shutdownNow()
    }
}
