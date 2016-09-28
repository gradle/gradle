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
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.junit.Rule
import spock.lang.Subject

@Subject(SingleMessageLogger)
class SingleMessageLoggerTest extends ConcurrentSpec {
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def cleanup() {
        SingleMessageLogger.reset()
    }

    def "logs deprecation warning once until reset"() {
        when:
        SingleMessageLogger.nagUserWith("nag")
        SingleMessageLogger.nagUserWith("nag")

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith('nag')

        when:
        SingleMessageLogger.reset()
        SingleMessageLogger.nagUserWith("nag")

        then:
        events.size() == 2
        events[0].message.startsWith('nag')
        events[1].message.startsWith('nag')
    }

    def "does not log warning while disabled with factory"() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = SingleMessageLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            SingleMessageLogger.nagUserWith("nag")
            return "result"
        }
        0 * _

        and:
        outputEventListener.events.empty
    }

    def "does not log warning while disabled with action"() {
        given:
        def action = Mock(Runnable)

        when:
        SingleMessageLogger.whileDisabled(action)

        then:
        1 * action.run()
        0 * _

        and:
        outputEventListener.events.empty
    }

    def "warnings are disabled for the current thread only"() {
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
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith('nag')
    }

    def "deprecation message has next major version"() {
        given:
        def major = GradleVersion.current().nextMajor

        when:
        SingleMessageLogger.nagUserOfDeprecated("foo", "bar")

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith("foo has been deprecated and is scheduled to be removed in Gradle ${major.version}. bar.")
    }
}
