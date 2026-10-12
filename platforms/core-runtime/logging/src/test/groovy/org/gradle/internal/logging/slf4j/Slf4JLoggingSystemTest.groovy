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
package org.gradle.internal.logging.slf4j

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.time.Time
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

class Slf4JLoggingSystemTest extends Specification {

    Logger logger = LoggerFactory.getLogger("cat1")
    OutputEventListener listener = Mock()
    Slf4jLoggingSystem loggingSystem = new Slf4jLoggingSystem(listener)

    def cleanup() {
        def context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory()
        context.reset()
    }

    private void startAtLevel(LogLevel logLevel) {
        loggingSystem.setLevel(logLevel)
        loggingSystem.startCapture()
    }

    def routesSlf4jLogEventsToOutputEventListener() {
        when:
        startAtLevel(LogLevel.INFO)
        logger.info('message')

        then:
        1 * listener.onOutput({ it.category == 'cat1' && it.message == 'message' && it.logLevel == LogLevel.INFO && it.throwable == null })
        0 * listener._
    }

    def "does not route slf4j events until capture is started"() {
        given:
        def context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory()
        context.setOutputEventListener(Stub(OutputEventListener))

        when:
        loggingSystem.setLevel(LogLevel.INFO)
        logger.info('message')

        then:
        0 * listener._
    }

    def includesThrowableInLogEvent() {
        def failure = new RuntimeException()

        when:
        startAtLevel(LogLevel.INFO)
        logger.info('message', failure)

        then:
        1 * listener.onOutput({ it.category == 'cat1' && it.message == 'message' && it.logLevel == LogLevel.INFO && it.throwable == failure })
        0 * listener._
    }

    def mapsSlf4jLogLevelsToGradleLogLevels() {
        when:
        startAtLevel(LogLevel.DEBUG)

        logger.debug('debug')
        logger.info('info')
        logger.info(Logging.LIFECYCLE, 'lifecycle')
        logger.info(Logging.QUIET, 'quiet')
        logger.warn('warn')
        logger.error('error')

        then:
        1 * listener.onOutput({ it.message == 'debug' && it.logLevel == LogLevel.DEBUG })
        1 * listener.onOutput({ it.message == 'info' && it.logLevel == LogLevel.INFO })
        1 * listener.onOutput({ it.message == 'lifecycle' && it.logLevel == LogLevel.LIFECYCLE })
        1 * listener.onOutput({ it.message == 'quiet' && it.logLevel == LogLevel.QUIET })
        1 * listener.onOutput({ it.message == 'warn' && it.logLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.message == 'error' && it.logLevel == LogLevel.ERROR })
        0 * listener._
    }

    def formatsLogMessage() {
        when:
        startAtLevel(LogLevel.INFO)
        logger.info('message {} {}', 'arg1', 'arg2')

        then:
        1 * listener.onOutput({ it.message == 'message arg1 arg2' })
        0 * listener._
    }

    def attachesATimestamp() {
        when:
        startAtLevel(LogLevel.INFO)
        logger.info('message')

        then:
        1 * listener.onOutput({ it.timestamp >= Time.clock().currentTime - 1000 })
        0 * listener._
    }

    def filtersLifecycleAndLowerWhenConfiguredAtQuietLevel() {
        when:
        startAtLevel(LogLevel.QUIET)

        logger.trace('trace')
        logger.debug('debug')
        logger.info('info')
        logger.info(Logging.LIFECYCLE, 'lifecycle')
        logger.info(Logging.QUIET, 'quiet')
        logger.warn('warn')
        logger.error('error')

        then:
        1 * listener.onOutput({ it.message == 'quiet' && it.logLevel == LogLevel.QUIET })
        1 * listener.onOutput({ it.message == 'error' && it.logLevel == LogLevel.ERROR })
        0 * listener._
    }

    def filtersInfoAndLowerWhenConfiguredAtLifecycleLevel() {
        when:
        startAtLevel(LogLevel.LIFECYCLE)

        logger.trace('trace')
        logger.debug('debug')
        logger.info('info')
        logger.info(Logging.LIFECYCLE, 'lifecycle')
        logger.info(Logging.QUIET, 'quiet')
        logger.warn('warn')
        logger.error('error')

        then:
        1 * listener.onOutput({ it.message == 'lifecycle' && it.logLevel == LogLevel.LIFECYCLE })
        1 * listener.onOutput({ it.message == 'quiet' && it.logLevel == LogLevel.QUIET })
        1 * listener.onOutput({ it.message == 'warn' && it.logLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.message == 'error' && it.logLevel == LogLevel.ERROR })
        0 * listener._
    }

    def filtersDebugAndLowerWhenConfiguredAtInfoLevel() {
        when:
        startAtLevel(LogLevel.INFO)

        logger.trace('trace')
        logger.debug('debug')
        logger.info('info')
        logger.info(Logging.LIFECYCLE, 'lifecycle')
        logger.info(Logging.QUIET, 'quiet')
        logger.warn('warn')
        logger.error('error')

        then:
        1 * listener.onOutput({ it.message == 'info' && it.logLevel == LogLevel.INFO })
        1 * listener.onOutput({ it.message == 'lifecycle' && it.logLevel == LogLevel.LIFECYCLE })
        1 * listener.onOutput({ it.message == 'quiet' && it.logLevel == LogLevel.QUIET })
        1 * listener.onOutput({ it.message == 'warn' && it.logLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.message == 'error' && it.logLevel == LogLevel.ERROR })
        0 * listener._
    }

    def filtersTraceWhenConfiguredAtDebugLevel() {
        when:
        startAtLevel(LogLevel.DEBUG)

        logger.trace('trace')
        logger.debug('debug')
        logger.info('info')
        logger.info(Logging.LIFECYCLE, 'lifecycle')
        logger.info(Logging.QUIET, 'quiet')
        logger.warn('warn')
        logger.error('error')

        then:
        1 * listener.onOutput({ it.message == 'debug' && it.logLevel == LogLevel.DEBUG })
        1 * listener.onOutput({ it.message == 'info' && it.logLevel == LogLevel.INFO })
        1 * listener.onOutput({ it.message == 'lifecycle' && it.logLevel == LogLevel.LIFECYCLE })
        1 * listener.onOutput({ it.message == 'quiet' && it.logLevel == LogLevel.QUIET })
        1 * listener.onOutput({ it.message == 'warn' && it.logLevel == LogLevel.WARN })
        1 * listener.onOutput({ it.message == 'error' && it.logLevel == LogLevel.ERROR })
        0 * listener._
    }

    def "changing the level of a started system takes effect immediately"() {
        when:
        startAtLevel(LogLevel.INFO)
        loggingSystem.setLevel(LogLevel.QUIET)
        logger.info('filtered')

        then:
        0 * listener._
    }

    def "restore returns the slf4j routing to the previous owner"() {
        // Two logging scopes in one process, each with its own adapter sharing the JVM-wide
        // slf4j binding
        def outerListener = Mock(OutputEventListener)
        def outerScope = new Slf4jLoggingSystem(outerListener)

        given:
        outerScope.setLevel(LogLevel.INFO)
        outerScope.startCapture()

        when: "a nested scope takes over the slf4j routing"
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.DEBUG)
        loggingSystem.startCapture()
        logger.info('nested')

        then:
        1 * listener.onOutput({ it.message == 'nested' })
        0 * outerListener._

        when: "the nested scope is restored"
        loggingSystem.restore(snapshot)
        logger.info('outer')

        then:
        1 * outerListener.onOutput({ it.message == 'outer' })
        0 * listener._
    }

    def "restore returns the log level of the previous owner"() {
        def outerScope = new Slf4jLoggingSystem(Stub(OutputEventListener))
        def context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory()

        given:
        outerScope.setLevel(LogLevel.WARN)
        outerScope.startCapture()

        when:
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.DEBUG)
        loggingSystem.startCapture()

        then:
        context.level == LogLevel.DEBUG

        when:
        loggingSystem.restore(snapshot)

        then:
        context.level == LogLevel.WARN
    }

    def "scope re-establishes its listener when started again after a restore"() {
        def otherScope = new Slf4jLoggingSystem(Mock(OutputEventListener))

        given: "the scope has been started and stopped once"
        otherScope.setLevel(LogLevel.INFO)
        otherScope.startCapture()
        def snapshot = loggingSystem.snapshot()
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()
        loggingSystem.restore(snapshot)

        when: "the scope is started a second time at the same level"
        loggingSystem.setLevel(LogLevel.INFO)
        loggingSystem.startCapture()
        logger.info('message')

        then:
        1 * listener.onOutput({ it.message == 'message' })
    }

    def "setLevel does nothing until started"() {
        given:
        def context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory()
        context.setLevel(LogLevel.LIFECYCLE)

        when:
        loggingSystem.setLevel(LogLevel.DEBUG)

        then:
        context.level == LogLevel.LIFECYCLE

        when:
        loggingSystem.startCapture()

        then:
        context.level == LogLevel.DEBUG
    }

}
