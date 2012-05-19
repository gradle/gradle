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

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 11/4/11
 */
class StandardStreamsLoggerTest extends Specification {
//    def logger = Mock(OutputEventListener)
//    def test = new DefaultTestDescriptor("1", "DogTest", "should bark")
//    def event = new DefaultTestOutputEvent(Destination.StdOut, "woof!")
//
//    def "does not log anything if showStandardStreams is false"() {
//        given:
//        def streamsLogger = new StandardStreamsLogger(logger, LogLevel.INFO, { showStandardStreams: false } as TestLogging)
//
//        when:
//        streamsLogger.onOutput(test, event)
//
//        then:
//        0 * logger._
//    }
//
//    def "includes header once"() {
//        given:
//        def streamsLogger = new StandardStreamsLogger(logger, LogLevel.INFO, { showStandardStreams: true } as TestLogging)
//
//        when:
//        streamsLogger.onOutput(test, event)
//
//        then:
//        1 * logger.onOutput({ it.toString().contains("should bark") })
//        1 * logger.onOutput({ it.toString().contains("woof!")})
//
//        when:
//        streamsLogger.onOutput(test, event)
//        streamsLogger.onOutput(test, event)
//
//        then:
//        2 * logger.onOutput({ !it.toString().contains("should bark") && it.toString().contains("woof!")})
//    }
//
//    def "includes header once per series of output events"() {
//        given:
//        def testTwo = new DefaultTestDescriptor("2", "DogTest", "should growl")
//        def eventTwo = new DefaultTestOutputEvent(Destination.StdOut, "grrr!")
//        def streamsLogger = new StandardStreamsLogger(logger, LogLevel.INFO, { showStandardStreams: true } as TestLogging)
//
//        when:
//        streamsLogger.onOutput(test, event)
//        streamsLogger.onOutput(test, event)
//
//        then:
//        1 * logger.onOutput({ it.toString().contains("should bark") })
//        2 * logger.onOutput({ !it.toString().contains("should bark") && it.toString().contains("woof!") })
//        0 * _
//
//        when:
//        streamsLogger.onOutput(testTwo, eventTwo)
//        streamsLogger.onOutput(testTwo, eventTwo)
//
//        then:
//        1 * logger.onOutput({ it.toString().contains("should growl") })
//        2 * logger.onOutput({ !it.toString().contains("should growl") && it.toString().contains("grrr!") })
//        0 * _
//
//        when:
//        //let's say test one is still pushing some output, include the header accordingly
//        streamsLogger.onOutput(test, event)
//        streamsLogger.onOutput(test, event)
//
//        then:
//        1 * logger.onOutput({ it.toString().contains("should bark") })
//        2 * logger.onOutput({ !it.toString().contains("should bark") && it.toString().contains("woof!") })
//        0 * _
//    }
}
