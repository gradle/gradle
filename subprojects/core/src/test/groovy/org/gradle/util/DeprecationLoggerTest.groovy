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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import org.gradle.internal.Factory
import org.gradle.logging.LoggingTestHelper
import spock.lang.Specification

class DeprecationLoggerTest extends Specification {
    private final Appender<ILoggingEvent> appender = Mock()
    private final LoggingTestHelper helper = new LoggingTestHelper(appender)

    public void setup() {
        helper.attachAppender()
    }

    public void cleanup() {
        helper.detachAppender()
        DeprecationLogger.reset()
    }

    def "logs deprecation warning once"() {
        when:
        DeprecationLogger.nagUserWith("nag")
        DeprecationLogger.nagUserWith("nag")

        then:
        1 * appender.doAppend(!null) >> { ILoggingEvent event ->
            assert event.formattedMessage == 'nag'
        }
        0 * appender._
    }

    def "does not log warning while disabled with factory"() {
        Factory<String> factory = Mock()

        when:
        def result = DeprecationLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            DeprecationLogger.nagUserWith("nag")
            return "result"
        }
        0 * _._
    }

    def "does not log warning while disabled with action"() {
        Runnable action = Mock()

        when:
        DeprecationLogger.whileDisabled(action)

        then:
        1 * action.run()
        0 * _._
    }
}
