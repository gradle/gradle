/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.logging.internal.slf4j

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.logging.internal.LogEvent
import org.gradle.logging.internal.OutputEventListener
import org.slf4j.Marker
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.logging.LogLevel.*
import static org.slf4j.Logger.ROOT_LOGGER_NAME

@Unroll
class OutputEventListenerBackedLoggerTest extends Specification {

    List<LogEvent> events = []
    OutputEventListenerBackedLoggerContext context

    def setup() {
        context = new OutputEventListenerBackedLoggerContext(System.out, System.err)
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

        void verify(boolean eventExpected) {
            if (!eventExpected) {
                assert events.size() == 0
                return
            }

            assert events.size() == 1
            LogEvent event = events.remove(0)
            assert event.category == category
            assert event.message == message
            assert event.timestamp >= timestamp
            assert event.throwable == throwable
            assert event.logLevel == logLevel
        }
    }

    private OutputEventListenerBackedLogger logger(String name) {
        context.getLogger(name)
    }

    private OutputEventListenerBackedLogger rootLogger() {
        logger(ROOT_LOGGER_NAME)
    }

    private OutputEventListenerBackedLogger parent() {
        logger("foo")
    }

    private OutputEventListenerBackedLogger logger() {
        logger("foo.bar")
    }

    private OutputEventListenerBackedLogger child() {
        logger("foo.bar.fizz")
    }

    private OutputEventListenerBackedLogger grandChild() {
        logger("foo.bar.fizz.buzz")
    }

    def "log level is inherited from parent upon creation"() {
        when:
        parent().level = WARN

        then:
        logger().effectiveLevel == WARN
    }

    def "log level propagates when it's set on ancestor"() {
        expect:
        logger().effectiveLevel == LIFECYCLE
        child().effectiveLevel == LIFECYCLE

        when:
        parent().level = WARN

        then:
        logger().effectiveLevel == WARN
        child().effectiveLevel == WARN
    }

    def "log level does not propagate if explicit level is set on descendant"() {
        when:
        child().level = WARN
        parent().level = INFO

        then:
        logger().effectiveLevel == INFO
        child().effectiveLevel == WARN
        grandChild().effectiveLevel == WARN
    }

    def "can revert to parent's level"() {
        when:
        parent().level = WARN
        logger().level = ERROR

        then:
        logger().effectiveLevel == ERROR

        when:
        logger().level = null

        then:
        logger().effectiveLevel == WARN
        child().effectiveLevel == WARN
    }

    def "cannot set root level to null"() {
        when:
        rootLogger().level = null

        then:
        IllegalArgumentException e = thrown()
        e.message == "The level of the root logger cannot be set to null"
    }

    def "disabling logger propagates when it's set on ancestor"() {
        expect:
        !logger().disabled
        !child().disabled

        when:
        parent().disable()

        then:
        parent().disabled
        logger().disabled
        child().disabled
    }

    def "logger is disabled upon creation if it's parent is disabled"() {
        when:
        logger().disable()

        then:
        child().disabled
    }

    def "isTraceEnabled returns false when level is #level"() {
        when:
        rootLogger().level = level

        then:
        !rootLogger().traceEnabled
        !rootLogger().isTraceEnabled(null)

        where:
        level << LogLevel.values()
    }

    def "isDebugEnabled returns #enabled when level is #level"() {
        when:
        rootLogger().level = level

        then:
        rootLogger().debugEnabled == enabled
        rootLogger().isDebugEnabled(null) == enabled


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
        rootLogger().level = level

        then:
        rootLogger().infoEnabled == enabled
        rootLogger().isInfoEnabled(null) == enabled


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
        rootLogger().level = level

        then:
        rootLogger().isInfoEnabled(Logging.LIFECYCLE) == enabled


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
        rootLogger().level = level

        then:
        rootLogger().isInfoEnabled(Logging.QUIET) == enabled


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
        rootLogger().level = level

        then:
        rootLogger().warnEnabled == enabled
        rootLogger().isWarnEnabled(null) == enabled


        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | false
        ERROR     | false
    }

    def "isErrorEnabled returns #enabled when level is #level"() {
        when:
        rootLogger().level = level

        then:
        rootLogger().errorEnabled == enabled
        rootLogger().isErrorEnabled(null) == enabled


        where:
        level     | enabled
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | true
        ERROR     | true
    }

    def "when logger is disabled then logging is not enabled when level is #level"() {
        when:
        rootLogger().level = level
        rootLogger().disable()

        then:
        !rootLogger().isTraceEnabled()
        !rootLogger().isTraceEnabled(null)
        !rootLogger().isDebugEnabled()
        !rootLogger().isDebugEnabled(null)
        !rootLogger().isInfoEnabled()
        !rootLogger().isInfoEnabled(null)
        !rootLogger().isInfoEnabled(Logging.LIFECYCLE)
        !rootLogger().isInfoEnabled(Logging.QUIET)
        !rootLogger().isWarnEnabled()
        !rootLogger().isWarnEnabled(null)
        !rootLogger().isErrorEnabled()
        !rootLogger().isErrorEnabled(null)

        where:
        level << LogLevel.values()
    }

    def "trace calls do nothing when level is #level"() {
        given:
        context.outputEventListener = Mock(OutputEventListener)

        and:
        rootLogger().level = level

        when:
        rootLogger().trace(message)
        rootLogger().trace(message, new Exception())
        rootLogger().trace(message, arg1)
        rootLogger().trace(message, arg1, arg2)
        rootLogger().trace(message, arg1, arg2, arg3)
        rootLogger().trace((Marker) null, message)
        rootLogger().trace((Marker) null, message, new Exception())
        rootLogger().trace((Marker) null, message, arg1)
        rootLogger().trace((Marker) null, message, arg1, arg2)
        rootLogger().trace((Marker) null, message, arg1, arg2, arg3)

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
        rootLogger().level = level

        when:
        rootLogger().debug("message")

        then:
        singleLogEvent().message("message").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(DEBUG).timestamp(now).throwable(throwable).verify(eventExpected)

        when:
        rootLogger().debug((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(DEBUG).timestamp(now).verify(eventExpected)

        when:
        rootLogger().debug((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(DEBUG).timestamp(now).throwable(throwable).verify(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | false
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls work as expected when level is #level"() {
        given:
        rootLogger().level = level

        when:
        rootLogger().info("message")

        then:
        singleLogEvent().message("message").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(INFO).timestamp(now).throwable(throwable).verify(eventExpected)

        when:
        rootLogger().info((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(INFO).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(INFO).timestamp(now).throwable(throwable).verify(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | false
        WARN      | false
        QUIET     | false
        ERROR     | false

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls with LIFECYCLE marker work as expected when level is #level"() {
        given:
        rootLogger().level = level

        when:
        rootLogger().info(Logging.LIFECYCLE, "message")

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.LIFECYCLE, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(LIFECYCLE).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.LIFECYCLE, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(LIFECYCLE).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.LIFECYCLE, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(LIFECYCLE).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.LIFECYCLE, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(LIFECYCLE).timestamp(now).throwable(throwable).verify(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | false
        QUIET     | false
        ERROR     | false

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "info calls with QUIET marker work as expected when level is #level"() {
        given:
        rootLogger().level = level

        when:
        rootLogger().info(Logging.QUIET, "message")

        then:
        singleLogEvent().message("message").logLevel(QUIET).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.QUIET, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(QUIET).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.QUIET, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(QUIET).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.QUIET, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(QUIET).timestamp(now).verify(eventExpected)

        when:
        rootLogger().info(Logging.QUIET, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(QUIET).timestamp(now).throwable(throwable).verify(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | true
        ERROR     | false

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "warn calls work as expected when level is #level"() {
        given:
        rootLogger().level = level

        when:
        rootLogger().warn("message")

        then:
        singleLogEvent().message("message").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(WARN).timestamp(now).throwable(throwable).verify(eventExpected)

        when:
        rootLogger().warn((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(WARN).timestamp(now).verify(eventExpected)

        when:
        rootLogger().warn((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(WARN).timestamp(now).throwable(throwable).verify(eventExpected)

        where:
        level     | eventExpected
        DEBUG     | true
        INFO      | true
        LIFECYCLE | true
        WARN      | true
        QUIET     | false
        ERROR     | false

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    def "error calls work as expected when level is #level"() {
        given:
        rootLogger().level = level

        when:
        rootLogger().error("message")

        then:
        singleLogEvent().message("message").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error("{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error("{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error("{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error("message", throwable)

        then:
        singleLogEvent().message("message").logLevel(ERROR).timestamp(now).throwable(throwable).verify(true)

        when:
        rootLogger().error((Marker)null, "message")

        then:
        singleLogEvent().message("message").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error((Marker)null, "{}", arg1)

        then:
        singleLogEvent().message("arg1").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error((Marker)null, "{} {}", arg1, arg2)

        then:
        singleLogEvent().message("arg1 arg2").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error((Marker)null, "{} {} {}", arg1, arg2, arg3)

        then:
        singleLogEvent().message("arg1 arg2 arg3").logLevel(ERROR).timestamp(now).verify(true)

        when:
        rootLogger().error((Marker)null, "message", throwable)

        then:
        singleLogEvent().message("message").logLevel(ERROR).timestamp(now).throwable(throwable).verify(true)

        where:
        level << LogLevel.values()

        now = System.currentTimeMillis()
        throwable = new Throwable()
        arg1 = "arg1"
        arg2 = "arg2"
        arg3 = "arg3"
    }

    private String stacktrace(Exception e) {
        def stream = new ByteArrayOutputStream()
        e.printStackTrace(new PrintStream(stream))
        stream.toString()
    }
}
