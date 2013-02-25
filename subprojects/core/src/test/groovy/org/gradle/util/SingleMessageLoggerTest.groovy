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
import org.gradle.logging.ConfigureLogging
import org.gradle.logging.TestAppender
import org.junit.Rule
import spock.lang.Specification

class SingleMessageLoggerTest extends Specification {
    final TestAppender appender = new TestAppender()
    @Rule final ConfigureLogging logging = new ConfigureLogging(appender)

    public void cleanup() {
        SingleMessageLogger.reset()
    }

    def "logs deprecation warning once"() {
        when:
        SingleMessageLogger.nagUserWith("nag")
        SingleMessageLogger.nagUserWith("nag")

        then:
        appender.toString() == '[WARN nag]'
    }

    def "does not log warning while disabled with factory"() {
        Factory<String> factory = Mock()

        when:
        def result = SingleMessageLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            SingleMessageLogger.nagUserWith("nag")
            return "result"
        }
        0 * _._
    }

    def "does not log warning while disabled with action"() {
        Runnable action = Mock()

        when:
        SingleMessageLogger.whileDisabled(action)

        then:
        1 * action.run()
        0 * _._
    }

    def "deprecation message has next major version"() {
        given:
        def major = GradleVersion.current().major

        expect:
        major != -1

        when:
        SingleMessageLogger.nagUserOfDeprecated("foo", "bar")

        then:
        appender.toString() == "[WARN foo has been deprecated and is scheduled to be removed in Gradle ${major + 1}.0. bar.]"
    }
}
