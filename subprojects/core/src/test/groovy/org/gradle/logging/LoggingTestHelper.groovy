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

package org.gradle.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level
import org.junit.rules.ExternalResource
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext

import java.util.logging.LogManager

class LoggingTestHelper extends ExternalResource {
    private final Appender<ILoggingEvent> appender;
    private Logger logger;

    def LoggingTestHelper(appender) {
        this.appender = appender;
    }

    @Override
    protected void before() {
        attachAppender()
    }

    @Override
    protected void after() {
        detachAppender()
    }

    public void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ROOT");
        logger.detachAndStopAllAppenders()
        logger.addAppender(appender)
        logger.setLevel(Level.ALL)
    }

    public void detachAppender() {
        logger.detachAppender(appender)
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory()
        lc.reset()
        LogManager.getLogManager().reset()
    }

    public void setLevel(Level level) {
        logger.setLevel(level)
    }
}
