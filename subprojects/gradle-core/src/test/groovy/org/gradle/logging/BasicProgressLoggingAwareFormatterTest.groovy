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

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import org.gradle.api.logging.Logging
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class BasicProgressLoggingAwareFormatterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final StringBuilder message = new StringBuilder()
    private final BasicProgressLoggingAwareFormatter formatter = new BasicProgressLoggingAwareFormatter((LoggerContext) LoggerFactory.getILoggerFactory(), message)

    @Test
    public void logsEventWithMessage() {
        formatter.format(event('message'))

        assertThat(message.toString(), equalTo(String.format('message%n')))
    }

    @Test
    public void logsEventWithMessageAndException() {
        formatter.format(event('message', new RuntimeException('broken')))

        assertThat(message.toString(), startsWith(String.format('message%n')))
        assertThat(message.toString(), containsString('java.lang.RuntimeException: broken'))
    }

    @Test
    public void logsProgressMessages() {
        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description complete%n')))
    }

    @Test
    public void logsProgressStatusMessages() {
        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description .. complete%n')))
    }

    @Test
    public void logsNestedProgressMessages() {
        formatter.format(event('description1', Logging.PROGRESS_STARTED))
        formatter.format(event('description2', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete2', Logging.PROGRESS_COMPLETE))
        formatter.format(event('complete1', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description1%ndescription2 .. complete2%ncomplete1%n')))
    }

    @Test
    public void logsMixOfProgressAndOtherMessages() {
        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('message'))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description .%nmessage%n. complete%n')))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatus() {
        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description%n')))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatusAndOtherMessages() {
        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('message'))
        formatter.format(event('', Logging.PROGRESS_COMPLETE))

        assertThat(message.toString(), equalTo(String.format('description%nmessage%n')))
    }

    private ILoggingEvent event(String text, Marker marker) {
        event(text, null, marker)
    }

    private ILoggingEvent event(String text, Throwable failure = null, marker = null) {
        IThrowableProxy throwableProxy = failure == null ? null : new ThrowableProxy(failure)
        [getThrowableProxy: {throwableProxy}, getFormattedMessage: {text}, getMarker: {marker}] as ILoggingEvent
    }
}
