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
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.operations.BuildOperationIdentifierRegistry
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.slf4j.Marker
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

import static org.gradle.api.logging.LogLevel.*
import static org.slf4j.Logger.ROOT_LOGGER_NAME

@Unroll
class OutputEventListenerBackedLoggerTest extends Specification {

    final List<LogEvent> events = []
    final long now = TimeUnit.MILLISECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS)
    final TimeProvider timeProvider = new TimeProvider() {
        @Override
        long getCurrentTime() {
            return now
        }

    }
    final OutputEventListenerBackedLoggerContext context = new OutputEventListenerBackedLoggerContext(System.out, System.err, timeProvider)

    def setup() {
        context.outputEventListener = Mock(OutputEventListener) {
            onOutput(_) >> { LogEvent event -> events << event }
        }
    }

    def cleanup() {
        assert !events
    }

    private SingleLogEventSpecificationBuilder singleLogEvent() {
        new SingleLogEventSpecificationBuilder()
    }

    private class SingleLogEventSpecificationBuilder {
        private String category = ROOT_LOGGER_NAME
        private String message
        private long timestamp
        private Throwable throwable
        private LogLevel logLevel
        private OperationIdentifier operationIdentifier
        private boolean eventExpected = true

        SingleLogEventSpecificationBuilder message(String message) {
            this.message = message
            this
        }

        SingleLogEventSpecificationBuilder timestamp(long timestamp) {
            this.timestamp = timestamp
            this
        }

        SingleLogEventSpecificationBuilder throwable(Throwable throwable) {
            this.throwable = throwable
            this
        }

        SingleLogEventSpecificationBuilder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel
            this
        }

        SingleLogEventSpecificationBuilder operationIdentifier(OperationIdentifier operationIdentifier) {
            this.operationIdentifier = operationIdentifier
            this
        }

        SingleLogEventSpecificationBuilder eventExpected(boolean eventExpected) {
            this.eventExpected = eventExpected
            this
        }

        boolean asBoolean() {
            if (!eventExpected) {
                assert events.size() == 0
                return true
            }

            assert events.size() == 1
            LogEvent event = events.remove(0)
            assert event.category == category
            assert event.message == message
            assert event.timestamp == now
            assert event.throwable == throwable
            assert event.logLevel == logLevel
            assert event.buildOperationId == operationIdentifier
            return true
        }
    }

    private Logger logger(String name) {
        context.getLogger(name)
    }

    private Logger logger() {
        logger(ROOT_LOGGER_NAME)
    }

    private void setGlobalLevel(LogLevel level) {
        context.level = level
    }

    def "isTraceEnabled returns false when level is #level"() {
        when:
        globalLevel = level

        then:
        !logger().traceEnabled
        !logger().isTraceEnabled(null)

        where:
        level << LogLevel.values()
    }

    def "isDebugEnabled returns #enabled when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().debugEnabled == enabled
        logger().isDebugEnabled(null) == enabled
        logger().isEnabled(DEBUG) == enabled


        where:
        level     | enabled
        DEBUG     | true
        INFO      | false
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false
    }

    def "isInfoEnabled returns #enabled when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().infoEnabled == enabled
        logger().isInfoEnabled(null) == enabled
        logger().isEnabled(INFO) == enabled

        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false
    }

    def "isInfoEnabled with LIFECYCLE marker returns #enabled when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().isInfoEnabled(Logging.LIFECYCLE) == enabled
        logger().lifecycleEnabled == enabled
        logger().isEnabled(LIFECYCLE) == enabled

        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | false
        QUIET     | false
        ERROR     | false
    }

    def "isInfoEnabled with QUIET marker returns #enabled when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().isInfoEnabled(Logging.QUIET) == enabled
        logger().quietEnabled == enabled
        logger().isEnabled(QUIET) == enabled


        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | true
        ERROR     | false
    }

    def "isWarnEnabled returns #enabled when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().warnEnabled == enabled
        logger().isWarnEnabled(null) == enabled
        logger().isEnabled(WARN) == enabled


        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | false
        ERROR     | false
    }

    def "isErrorEnabled returns true when level is #level"() {
        when:
        globalLevel = level

        then:
        logger().errorEnabled
        logger().isErrorEnabled(null)
        logger().isEnabled(ERROR)


        where:
        level << LogLevel.values()
    }

    def "trace calls do nothing when level is #level"() {
        given:
        context.outputEventListener = Mock(OutputEventListener)

        and:
        globalLevel = level

        when:
        logger().trace(message)
        logger().trace(message, new Exception())
        logger().trace(message, arg1)
        logger().trace(message, arg1, arg2)
        logger().trace(message, arg1, arg2, arg3)
        logger().trace((Marker) null, message)
        logger().trace((Marker) null, message, new Exception())
        logger().trace((Marker) null, message, arg1)
        logger().trace((Marker) null, message, arg1, arg2)
        logger().trace((Marker) null, message, arg1, arg2, arg3)

        then:
        0 * context.outputEventListener._


        where:
        level << LogLevel.values()

        message = "message"
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "debug calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().debug("message")

        then:
        singleLogEvent().message("message").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().log(DEBUG, "message")

        then:
        singleLogEvent().message("message").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().log(DEBUG, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(DEBUG).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().log(DEBUG, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(DEBUG).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().debug((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(DEBUG).eventExpected(eventExpected)

        when:
        logger().debug((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(DEBUG).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().debug("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(DEBUG).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | false
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().info("message")

        then:
        singleLogEvent().message("message").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().log(INFO, "message")

        then:
        singleLogEvent().message("message").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().log(INFO, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(INFO).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().log(INFO, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(INFO).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().info((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(INFO).eventExpected(eventExpected)

        when:
        logger().info((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(INFO).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().info("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(INFO).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls with LIFECYCLE marker work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().info(Logging.LIFECYCLE, "message")

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().info(Logging.LIFECYCLE, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().info(Logging.LIFECYCLE, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().info(Logging.LIFECYCLE, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().info(Logging.LIFECYCLE, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | false
        QUIET     | false
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "lifecycle calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().lifecycle("message")

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().log(LIFECYCLE, "message")

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().lifecycle("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().log(LIFECYCLE, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().lifecycle("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().lifecycle("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(LIFECYCLE).eventExpected(eventExpected)

        when:
        logger().lifecycle("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().log(LIFECYCLE, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().lifecycle("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(LIFECYCLE).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | false
        QUIET     | false
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls with QUIET marker work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().info(Logging.QUIET, "message")

        then:
        singleLogEvent().message("message").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().info(Logging.QUIET, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().info(Logging.QUIET, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().info(Logging.QUIET, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().info(Logging.QUIET, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(QUIET).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | true
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "quiet calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().quiet("message")

        then:
        singleLogEvent().message("message").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().log(QUIET, "message")

        then:
        singleLogEvent().message("message").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().quiet("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().log(QUIET, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().quiet("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().quiet("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(QUIET).eventExpected(eventExpected)

        when:
        logger().quiet("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(QUIET).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().log(QUIET, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(QUIET).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().quiet("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(QUIET).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | true
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "warn calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().warn("message")

        then:
        singleLogEvent().message("message").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().log(WARN, "message")

        then:
        singleLogEvent().message("message").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().log(WARN, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(WARN).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().log(WARN, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(WARN).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().warn((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(WARN).eventExpected(eventExpected)

        when:
        logger().warn((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(WARN).throwable(throwable).eventExpected(eventExpected)

        when:
        logger().warn("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(WARN).throwable(throwable).eventExpected(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | false
        ERROR     | false

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "error calls work as expected when level is #level"() {
        given:
        globalLevel = level

        when:
        logger().error("message")

        then:
        singleLogEvent().message("message").logLevel(ERROR)

        when:
        logger().log(ERROR, "message")

        then:
        singleLogEvent().message("message").logLevel(ERROR)

        when:
        logger().error("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(ERROR)

        when:
        logger().log(ERROR, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(ERROR)

        when:
        logger().error("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(ERROR)

        when:
        logger().error("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(ERROR)

        when:
        logger().error("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(ERROR).throwable(throwable)

        when:
        logger().log(ERROR, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(ERROR).throwable(throwable)

        when:
        logger().error((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(ERROR)

        when:
        logger().error((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(ERROR)

        when:
        logger().error((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(ERROR)

        when:
        logger().error((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(ERROR)

        when:
        logger().error((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(ERROR).throwable(throwable)

        when:
        logger().error("There was a {} error", "bad", throwable)

        then:
        singleLogEvent().message("There was a bad error").logLevel(ERROR).throwable(throwable)

        where:
        level << LogLevel.values()

        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "log events include build operation id"() {
        given:
        def operationId = new OperationIdentifier(42L)
        BuildOperationIdentifierRegistry.setCurrentOperationIdentifier(operationId)

        when:
        logger().error('message')

        then:
        singleLogEvent().message('message').logLevel(ERROR).operationIdentifier(operationId).eventExpected(true)

        cleanup:
        BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier()
    }

    private String stacktrace(Exception e) {
        def stream = new ByteArrayOutputStream()
        e.printStackTrace(new PrintStream(stream))
        stream.toString()
    }

    def "logging from Apache HTTP wire logger is suppressed"() {
        when:
        logger(OutputEventListenerBackedLoggerContext.HTTP_CLIENT_WIRE_LOGGER_NAME).error("message")

        then:
        singleLogEvent().eventExpected(false)
    }

    def "logging from MetaInfExtensionModule logger is suppressed"() {
        when:
        logger(OutputEventListenerBackedLoggerContext.META_INF_EXTENSION_MODULE_LOGGER_NAME).error("message")

        then:
        singleLogEvent().eventExpected(false)
    }
}
