/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.logging

import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.events.OutputEventListener
import spock.lang.Specification

class LoggingTest extends Specification {
    private final OutputEventListener outputEventListener = Mock()
    private final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        logging.attachListener()
    }

    def cleanup() {
        logging.resetLogging()
    }

    def routesLogMessagesViaSlf4j() {
        Logger logger = Logging.getLogger(LoggingTest.class)

        when:
        logger.debug("debug")
        logger.info("info")
        logger.warn("warn")
        logger.lifecycle("lifecycle")
        logger.error("error")
        logger.quiet("quiet")
        logger.log(LogLevel.LIFECYCLE, "lifecycle via level")

        then:
        1 * outputEventListener.onOutput({ it.message == "debug" && it.logLevel == LogLevel.DEBUG })
        1 * outputEventListener.onOutput({ it.message == "info" && it.logLevel == LogLevel.INFO })
        1 * outputEventListener.onOutput({ it.message == "warn" && it.logLevel == LogLevel.WARN })
        1 * outputEventListener.onOutput({ it.message == "lifecycle" && it.logLevel == LogLevel.LIFECYCLE })
        1 * outputEventListener.onOutput({ it.message == "error" && it.logLevel == LogLevel.ERROR })
        1 * outputEventListener.onOutput({ it.message == "quiet" && it.logLevel == LogLevel.QUIET })
        1 * outputEventListener.onOutput({ it.message == "lifecycle via level" && it.logLevel == LogLevel.LIFECYCLE })
    }

    def ignoresTraceLevelLogging() {
        Logger logger = Logging.getLogger(LoggingTest.class)

        when:
        logger.trace("trace")

        then:
        0 * _._
    }

    def delegatesLevelIsEnabledToSlf4j() {

        when:
        logging.setLevel(LogLevel.WARN)

        then:
        Logger logger = Logging.getLogger(LoggingTest.class)
        logger.errorEnabled
        logger.quietEnabled
        logger.warnEnabled
        !logger.lifecycleEnabled
        !logger.infoEnabled
        !logger.debugEnabled
        !logger.traceEnabled

        logger.isEnabled(LogLevel.ERROR)
        !logger.isEnabled(LogLevel.INFO)
    }
}
