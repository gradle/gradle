/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.source


import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.TestOutputEventListener
import org.junit.Rule
import spock.lang.Specification

import java.util.logging.Level
import java.util.logging.Logger

class JavaUtilLoggingSystemTest extends Specification {
    final TestOutputEventListener outputEventListener = new TestOutputEventListener()
    @Rule final ConfigureLogging logging = new ConfigureLogging(outputEventListener)
    private final JavaUtilLoggingSystem configurer = new JavaUtilLoggingSystem()

    def routesJulToListener() {
        when:
        configurer.setLevel(LogLevel.INFO)
        configurer.startCapture()
        Logger.getLogger('test').info('info message')
        Logger.getLogger('test').severe('error message')

        then:
        outputEventListener.toString() == '[[INFO] [test] info message][[ERROR] [test] error message]'
    }

    def routesJulToListenerWithCorrectLevel() {
        when:
        configurer.setLevel(LogLevel.INFO)
        configurer.startCapture()
        Logger.getLogger('test').info('info message')
        Logger.getLogger('test').severe('error message')
        Logger.getLogger('test').fine('debug message')

        then:
        outputEventListener.toString() == '[[INFO] [test] info message][[ERROR] [test] error message]'
    }

    def stopsRoutingWhenRestored() {
        when:
        def snapshot = configurer.snapshot()
        configurer.setLevel(LogLevel.DEBUG)
        configurer.startCapture()
        Logger.getLogger('test').info('info message')
        configurer.restore(snapshot)
        Logger.getLogger('test').info('ignore me')

        then:
        outputEventListener.toString() == '[[INFO] [test] info message]'
    }

    def "Log level is not propagated if the logging system was not started"() {
        when:
        configurer.setLevel(LogLevel.DEBUG)

        then:
        Logger.getLogger("").getLevel() == Level.INFO
    }

    def "Starting without setting a log level does not crash, but no level is set"() {
        when:
        configurer.startCapture()

        then:
        Logger.getLogger("").getLevel() == null
    }

    def "Log level can be set before starting"() {
        when:
        configurer.setLevel(LogLevel.DEBUG)
        configurer.startCapture()

        then:
        Logger.getLogger("").getLevel() == Level.FINE
    }

    def "Log level can be set after starting"() {
        when:
        configurer.startCapture()
        configurer.setLevel(LogLevel.DEBUG)

        then:
        Logger.getLogger("").getLevel() == Level.FINE
    }

    def "Log level can be changed while running"() {
        when:
        configurer.startCapture()
        configurer.setLevel(LogLevel.LIFECYCLE)
        configurer.setLevel(LogLevel.DEBUG)

        then:
        Logger.getLogger("").getLevel() == Level.FINE
    }

    def "Log level can be changed before starting"() {
        when:
        configurer.setLevel(LogLevel.LIFECYCLE)
        configurer.setLevel(LogLevel.DEBUG)
        configurer.startCapture()

        then:
        Logger.getLogger("").getLevel() == Level.FINE
    }
}
