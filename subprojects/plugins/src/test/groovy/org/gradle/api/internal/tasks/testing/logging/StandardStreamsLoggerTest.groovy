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

package org.gradle.api.internal.tasks.testing.logging;


import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent
import org.gradle.api.tasks.testing.TestLogging
import org.gradle.api.tasks.testing.TestOutputEvent.Destination
import org.slf4j.Logger
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 11/4/11
 */
public class StandardStreamsLoggerTest extends Specification {

    def logger = Mock(Logger)
    def test = new DefaultTestDescriptor("1", "DogTest", "should bark")
    def event = new DefaultTestOutputEvent(Destination.StdOut, "woof!")

    def "does not show standard streams"() {
        given:
        def streamsLogger = new StandardStreamsLogger(logger, { showStandardStreams: false } as TestLogging)

        when:
        streamsLogger.onOutput(test, event)

        then:
        0 * logger._
    }

    def "includes header once"() {
        given:
        def streamsLogger = new StandardStreamsLogger(logger, { showStandardStreams: true } as TestLogging)

        when:
        streamsLogger.onOutput(test, event)

        then:
        1 * logger.info({ it.contains("should bark") })
        1 * logger.info({ it.contains("woof!")})

        when:
        streamsLogger.onOutput(test, event)
        streamsLogger.onOutput(test, event)

        then:
        2 * logger.info({ !it.contains("should bark") && it.contains("woof!")})
    }

    def "includes header once per series of output events"() {
        given:
        def testTwo = new DefaultTestDescriptor("2", "DogTest", "should growl")
        def eventTwo = new DefaultTestOutputEvent(Destination.StdOut, "grrr!")
        def streamsLogger = new StandardStreamsLogger(logger, { showStandardStreams: true } as TestLogging)

        when:
        streamsLogger.onOutput(test, event)
        streamsLogger.onOutput(test, event)

        then:
        1 * logger.info({ it.contains("should bark") })
        2 * logger.info({ !it.contains("should bark") && it.contains("woof!")})
        0 * logger._

        when:
        streamsLogger.onOutput(testTwo, eventTwo)
        streamsLogger.onOutput(testTwo, eventTwo)

        then:
        1 * logger.info({ it.contains("should growl") })
        2 * logger.info({ !it.contains("should growl") && it.contains("grrr!")})
        0 * logger._

        when:
        //let's say test one is still pushing some output, include the header accordingly
        streamsLogger.onOutput(test, event)
        streamsLogger.onOutput(test, event)

        then:
        1 * logger.info({ it.contains("should bark") })
        2 * logger.info({ !it.contains("should bark") && it.contains("woof!")})
        0 * logger._
    }

    def "uses error level for errors"() {
        def streamsLogger = new StandardStreamsLogger(logger, { showStandardStreams: true } as TestLogging)
        def event = new DefaultTestOutputEvent(Destination.StdErr, "boom!")

        when:
        streamsLogger.onOutput(test, event)

        then:
        1 * logger.info({ it.contains("should bark")})
        1 * logger.error({ it.contains("boom!")})
        0 * logger._
    }
}
