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

class TestOutputRedirectorTest extends Specification {

    TestResultProcessor processor = Mock(TestResultProcessor)
    StandardOutputRedirector redir = Mock(StandardOutputRedirector)

    @Subject redirector = new TestOutputRedirector(new FixedClock(123), processor, redir)

    def "starts redirecting output and error"() {
        when:
        redirector.setOutputOwner("1")
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

    def "allows setting output owner"() {
        when:
        redirector.setOutputOwner("1")
        redirector.startRedirecting()

        then:
        redirector.outForwarder.outputOwner == "1"
        redirector.errForwarder.outputOwner == "1"

        when:
        redirector.setOutputOwner("2")

        then:
        redirector.outForwarder.outputOwner == "2"
        redirector.errForwarder.outputOwner == "2"
    }

    def "passes output events"() {
        def f = new TestOutputRedirector.Forwarder(new FixedClock(123), processor, TestOutputEvent.Destination.StdErr)
        f.outputOwner = "5"

        when: f.onOutput("ala")

        then:
        1 * processor.output("5", { DefaultTestOutputEvent e ->
            e.destination == TestOutputEvent.Destination.StdErr
            e.message == "ala"
        })
        0 * _
    }
}
