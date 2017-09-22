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

class Slf4jLoggingConfigurerTest extends Specification {
    Logger logger = LoggerFactory.getLogger("cat1")
    OutputEventListener listener = Mock()
    Slf4jLoggingConfigurer configurer = new Slf4jLoggingConfigurer(listener)

    def cleanup() {
        def context = (OutputEventListenerBackedLoggerContext) LoggerFactory.getILoggerFactory()
        context.reset()
    }

    def routesSlf4jLogEventsToOutputEventListener() {
        when:
        configurer.configure(LogLevel.INFO)
        logger.info('message')

        then:
        1 * listener.onOutput({ it.category == 'cat1' && it.message == 'message' && it.logLevel == LogLevel.INFO && it.throwable == null })
        0 * listener._
    }

    def includesThrowableInLogEvent() {
        def failure = new RuntimeException()

        when:
        configurer.configure(LogLevel.INFO)
        logger.info('message', failure)

        then:
        1 * listener.onOutput({ it.category == 'cat1' && it.message == 'message' && it.logLevel == LogLevel.INFO && it.throwable == failure })
        0 * listener._
    }

    def mapsSlf4jLogLevelsToGradleLogLevels() {
        when:
        configurer.configure(LogLevel.DEBUG)

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
        configurer.configure(LogLevel.INFO)
        logger.info('message {} {}', 'arg1', 'arg2')

        then:
        1 * listener.onOutput({ it.message == 'message arg1 arg2' })
        0 * listener._
    }

    def attachesATimestamp() {
        when:
        configurer.configure(LogLevel.INFO)
        logger.info('message')

        then:
        1 * listener.onOutput({ it.timestamp >= Time.clock().currentTime - 1000 })
        0 * listener._
    }

    def filtersLifecycleAndLowerWhenConfiguredAtQuietLevel() {
        when:
        configurer.configure(LogLevel.QUIET)

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
        configurer.configure(LogLevel.LIFECYCLE)

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
        configurer.configure(LogLevel.INFO)

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
        configurer.configure(LogLevel.DEBUG)

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
}
