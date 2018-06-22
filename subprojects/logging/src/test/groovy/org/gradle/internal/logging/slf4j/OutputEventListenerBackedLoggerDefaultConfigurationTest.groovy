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

import org.gradle.api.logging.Logging
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.time.Time
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.util.TextUtil
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Specification

class OutputEventListenerBackedLoggerDefaultConfigurationTest extends Specification {

    @Rule RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()

    String getOut() {
        outputs.stdOut
    }

    String getErr() {
        outputs.stdErr
    }

    OutputEventListenerBackedLoggerContext context

    Logger logger() {
        if (context == null) {
            context = new OutputEventListenerBackedLoggerContext(Time.clock())
        }
        context.getLogger("foo")
    }

    def "messages logged below LIFECYCLE level are ignored"() {
        when:
        logger().trace("debug")
        logger().debug("debug")
        logger().info("debug")
        context.flush()

        then:
        out.empty
        err.empty
    }

    def "messages logged at LIFECYCLE, WARN and QUIET levels are directed to default output stream"() {
        when:
        logger().info(Logging.LIFECYCLE, "lifecycle")
        logger().warn("warn")
        logger().info(Logging.QUIET, "quiet")
        context.flush()

        then:
        err.empty
        out == TextUtil.toPlatformLineSeparators("""lifecycle
warn
quiet
""")
    }

    def "messages logged at ERROR level are directed to default error stream"() {
        when:
        logger().error("error")
        context.flush()

        then:
        out.empty
        err == TextUtil.toPlatformLineSeparators("""error
""")
    }

    def "messages are not lost when listener is attached"() {
        def listener = Mock(OutputEventListener)

        when:
        logger().info(Logging.LIFECYCLE, "lifecycle 1")
        logger().info(Logging.QUIET, "quiet 1")
        logger().info(Logging.LIFECYCLE, "lifecycle 2")
        context.setOutputEventListener(listener)
        logger().info(Logging.LIFECYCLE, "lifecycle 3")

        then:
        out == TextUtil.toPlatformLineSeparators("""lifecycle 1
quiet 1
lifecycle 2
""")
        1 * listener.onOutput(_)
        0 * listener._
    }

    private String stacktrace(Exception e) {
        def stream = new ByteArrayOutputStream()
        e.printStackTrace(new PrintStream(stream))
        stream.toString()
    }

    def "can log stacktraces"() {
        given:
        Exception e = new Exception();

        when:
        logger().warn("warn stacktrace coming", e)
        logger().error("error stacktrace coming", e)
        context.flush()

        then:
        out == TextUtil.toPlatformLineSeparators("""warn stacktrace coming
""" + stacktrace(e))
        err == TextUtil.toPlatformLineSeparators("""error stacktrace coming
""" + stacktrace(e))
    }
}
