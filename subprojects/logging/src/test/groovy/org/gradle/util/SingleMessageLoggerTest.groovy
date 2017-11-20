/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util

import org.gradle.internal.Factory
import org.gradle.internal.featurelifecycle.FeatureHandler
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Subject

import static org.gradle.internal.featurelifecycle.FeatureUsage.FeatureType.DEPRECATED
import static org.gradle.internal.featurelifecycle.FeatureUsage.FeatureType.INCUBATING

@Subject(SingleMessageLogger)
class SingleMessageLoggerTest extends ConcurrentSpec {
    def setup() {
        SingleMessageLogger.handler = Mock(FeatureHandler)
    }

    def 'new feature handler is created after reset'() {
        given:
        def original = SingleMessageLogger.handler

        when:
        SingleMessageLogger.reset()

        then:
        !original.is(SingleMessageLogger.handler)
    }

    def 'deprecations are delegated to handler'() {
        def capturedValues = []

        when:
        SingleMessageLogger.nagUserWith('1')
        SingleMessageLogger.nagUserWith('2')

        then:
        2 * SingleMessageLogger.handler.featureUsed(_) >> { args -> capturedValues.add(args[0]) }
        capturedValues[0].message == '1'
        capturedValues[0].type == DEPRECATED
        capturedValues[1].message == '2'
        capturedValues[1].type == DEPRECATED
    }

    def 'incubating warnings are delegated to handler'() {
        def capturedValues = []

        when:
        SingleMessageLogger.incubatingFeatureUsed('1')
        SingleMessageLogger.incubatingFeatureUsed('2')

        then:
        2 * SingleMessageLogger.handler.featureUsed(_) >> { args -> capturedValues.add(args[0]) }
        capturedValues[0].message == '1'
        capturedValues[0].type == INCUBATING
        capturedValues[1].message == '2'
        capturedValues[1].type == INCUBATING
    }

    def "does not log warning while disabled with factory"() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = SingleMessageLogger.whileDisabled(factory)

        then:
        result == 'result'
        1 * factory.create() >> {
            SingleMessageLogger.nagUserWith("nag")
            return "result"
        }

        and:
        0 * _
    }

    def "does not log warning while disabled with action"() {
        given:
        def action = Mock(Runnable)

        when:
        SingleMessageLogger.whileDisabled(action)

        then:
        1 * action.run()
        0 * _
    }

    def "warnings are disabled for the current thread only"() {
        def capturedValues = []

        when:
        async {
            start {
                thread.blockUntil.disabled
                SingleMessageLogger.nagUserWith("nag")
                instant.logged
            }
            start {
                SingleMessageLogger.whileDisabled {
                    instant.disabled
                    SingleMessageLogger.nagUserWith("ignored")
                    thread.blockUntil.logged
                }
            }
        }

        then:
        SingleMessageLogger.handler.featureUsed(_) >> { args -> capturedValues.add(args[0]) }
        capturedValues.size() == 1
        capturedValues[0].message == 'nag'
    }
}
