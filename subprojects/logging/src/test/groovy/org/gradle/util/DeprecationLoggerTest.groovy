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

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.Factory
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadcaster
import org.gradle.internal.featurelifecycle.UsageLocationReporter
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.internal.deprecation.DeprecationMessage
import org.junit.Rule
import spock.lang.Subject

@Subject(DeprecationLogger)
class DeprecationLoggerTest extends ConcurrentSpec {
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        DeprecationLogger.init(Mock(UsageLocationReporter), WarningMode.All, Mock(DeprecatedUsageBuildOperationProgressBroadcaster))
    }

    def cleanup() {
        DeprecationLogger.reset()
    }

    def "logs deprecation warning once until reset"() {
        when:
        DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("nag"))
        DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("nag"))

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith('nag')

        when:
        DeprecationLogger.reset()
        DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("nag"))

        then:
        events.size() == 2
        events[0].message.startsWith('nag')
        events[1].message.startsWith('nag')
    }

    def "does not log warning while disabled with factory"() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = DeprecationLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("nag"))
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
        DeprecationLogger.whileDisabled(action)

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
                DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("nag"))
                instant.logged
            }
            start {
                DeprecationLogger.whileDisabled {
                    instant.disabled
                    DeprecationLogger.nagUserWith(DeprecationMessage.specificThingHasBeenDeprecated("ignored"))
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
        DeprecationLogger.nagUserWith(DeprecationMessage
            .specificThingHasBeenDeprecated("foo")
            .withAdvice("bar."));

        then:
        def events = outputEventListener.events
        events.size() == 1
        events[0].message.startsWith("foo has been deprecated. This is scheduled to be removed in Gradle ${major.version}. bar.")
    }
}
