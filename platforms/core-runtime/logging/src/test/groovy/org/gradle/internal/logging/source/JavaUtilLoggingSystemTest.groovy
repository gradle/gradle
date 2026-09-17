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

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
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

    def "restore reinstates the root logger handlers that capturing displaced"() {
        given:
        def rootLogger = Logger.getLogger("")
        def originalHandler = new NoOpHandler()
        rootLogger.addHandler(originalHandler)
        def originalHandlers = rootLogger.handlers.toList()

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()

        then:
        !rootLogger.handlers.toList().contains(originalHandler)

        when:
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.toList() == originalHandlers

        cleanup:
        rootLogger.removeHandler(originalHandler)
    }

    def "closes handlers installed during the capturing scope when restored, but not the reinstated handlers"() {
        given:
        def rootLogger = Logger.getLogger("")
        def outerHandler = new NoOpHandler()
        rootLogger.addHandler(outerHandler)

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()
        def scopeHandler = new NoOpHandler()
        rootLogger.addHandler(scopeHandler)
        configurer.restore(snapshot)

        then: "the handler installed during the scope is removed and closed"
        scopeHandler.closed
        !rootLogger.handlers.toList().contains(scopeHandler)

        and: "the displaced outer handler is reinstated, still open"
        !outerHandler.closed
        rootLogger.handlers.toList().contains(outerHandler)

        cleanup:
        rootLogger.removeHandler(outerHandler)
    }

    def "a nested logging scope restores the outer scope's routing when restored"() {
        given:
        def outer = new JavaUtilLoggingSystem()
        def inner = new JavaUtilLoggingSystem()

        when:
        outer.setLevel(LogLevel.INFO)
        outer.startCapture()
        def snapshot = inner.snapshot()
        inner.setLevel(LogLevel.INFO)
        inner.startCapture()
        inner.restore(snapshot)
        Logger.getLogger('test').info('info message')

        then: "the outer scope's bridge handler is back in place and still routes"
        outputEventListener.toString() == '[[INFO] [test] info message]'
    }

    private static class NoOpHandler extends Handler {

        boolean closed

        @Override
        void publish(LogRecord record) {}

        @Override
        void flush() {}

        @Override
        void close() {
            closed = true
        }

    }

}
