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
import org.slf4j.bridge.SLF4JBridgeHandler
import spock.lang.Specification

import java.util.logging.ConsoleHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
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

    def "leaves unrelated root handlers attached and open while capturing"() {
        given:
        def rootLogger = Logger.getLogger("")
        def unrelated = new RecordingHandler()
        rootLogger.addHandler(unrelated)

        when:
        def snapshot = configurer.snapshot()
        configurer.setLevel(LogLevel.INFO)
        configurer.startCapture()
        Logger.getLogger('test').info('info message')

        then:
        rootLogger.handlers.toList().contains(unrelated)
        unrelated.messages == ['info message']

        when:
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.toList().contains(unrelated)
        !unrelated.closed

        cleanup:
        rootLogger.removeHandler(unrelated)
    }

    def "replaces console handlers while capturing and reattaches them unclosed when restored"() {
        given:
        def rootLogger = Logger.getLogger("")
        def console = new RecordingConsoleHandler()
        rootLogger.addHandler(console)

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()

        then:
        !rootLogger.handlers.toList().contains(console)

        when:
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.toList().contains(console)
        !console.closed

        cleanup:
        rootLogger.removeHandler(console)
    }

    def "removes its own bridge when restored"() {
        given:
        def rootLogger = Logger.getLogger("")

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()

        then:
        rootLogger.handlers.count { it instanceof SLF4JBridgeHandler } == 1

        when:
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.count { it instanceof SLF4JBridgeHandler } == 0
    }

    def "does not remove or close handlers added by others while capturing"() {
        given:
        def rootLogger = Logger.getLogger("")

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()
        def added = new RecordingHandler()
        rootLogger.addHandler(added)
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.toList().contains(added)
        !added.closed

        cleanup:
        rootLogger.removeHandler(added)
    }

    def "a nested logging scope replaces the outer scope's bridge and restores it when restored"() {
        given:
        def outer = new JavaUtilLoggingSystem()
        def inner = new JavaUtilLoggingSystem()

        when:
        outer.setLevel(LogLevel.INFO)
        outer.startCapture()
        def snapshot = inner.snapshot()
        inner.setLevel(LogLevel.INFO)
        inner.startCapture()
        Logger.getLogger('test').info('inner message')

        then: "only the inner scope's bridge routes"
        outputEventListener.toString() == '[[INFO] [test] inner message]'

        when:
        inner.restore(snapshot)
        Logger.getLogger('test').info('outer message')

        then: "the outer scope's bridge handler is back in place and still routes"
        outputEventListener.toString() == '[[INFO] [test] inner message][[INFO] [test] outer message]'
    }

    def "does not attach a replaced handler twice if it was reattached while capturing"() {
        given:
        def rootLogger = Logger.getLogger("")
        def console = new RecordingConsoleHandler()
        rootLogger.addHandler(console)

        when:
        def snapshot = configurer.snapshot()
        configurer.startCapture()
        rootLogger.addHandler(console)
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.count { it.is(console) } == 1

        cleanup:
        rootLogger.removeHandler(console)
    }

    def "can restore and capture again after the LogManager was reset while capturing"() {
        given:
        def rootLogger = Logger.getLogger("")
        def console = new RecordingConsoleHandler()
        rootLogger.addHandler(console)

        when: "the LogManager is reset while capturing, which removes and closes the bridge"
        def snapshot = configurer.snapshot()
        configurer.setLevel(LogLevel.INFO)
        configurer.startCapture()
        LogManager.logManager.reset()

        then:
        rootLogger.handlers.count { it instanceof SLF4JBridgeHandler } == 0

        when:
        configurer.restore(snapshot)

        then:
        rootLogger.handlers.toList().contains(console)
        !console.closed
        rootLogger.handlers.count { it instanceof SLF4JBridgeHandler } == 0

        when:
        configurer.setLevel(LogLevel.INFO)
        configurer.startCapture()
        Logger.getLogger('test').info('info message')

        then:
        outputEventListener.toString() == '[[INFO] [test] info message]'

        cleanup:
        rootLogger.removeHandler(console)
    }

    private static class RecordingHandler extends Handler {

        final List<String> messages = []
        boolean closed

        @Override
        void publish(LogRecord record) {
            messages << record.message
        }

        @Override
        void flush() {}

        @Override
        void close() {
            closed = true
        }

    }

    private static class RecordingConsoleHandler extends ConsoleHandler {

        boolean closed

        @Override
        void close() {
            closed = true
            super.close()
        }

    }

}
