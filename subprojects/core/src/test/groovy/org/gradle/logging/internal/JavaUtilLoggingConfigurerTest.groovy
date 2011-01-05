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

package org.gradle.logging.internal

import spock.lang.Specification
import java.util.logging.LogManager
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import org.gradle.api.logging.LogLevel
import java.util.logging.Logger
import ch.qos.logback.classic.Level
import org.gradle.logging.LoggingTestHelper

class JavaUtilLoggingConfigurerTest extends Specification {
    private final Appender<ILoggingEvent> appender = Mock()
    private final LoggingTestHelper helper = new LoggingTestHelper(appender)
    private final JavaUtilLoggingConfigurer configurer = new JavaUtilLoggingConfigurer()

    def setup() {
        helper.attachAppender()
    }

    def cleanup() {
        helper.detachAppender()
        LogManager.getLogManager().reset()
    }

    def routesJulToSlf4j() {
        when:
        configurer.configure(LogLevel.DEBUG)
        Logger.getLogger('test').info('info message')

        then:
        1 * appender.doAppend({ILoggingEvent event -> event.level == Level.INFO && event.message == 'info message'})
        0 * appender._
    }
}
