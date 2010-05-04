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
import ch.qos.logback.classic.Level
import org.gradle.api.logging.StandardOutputListener

@RunWith(JMock.class)
class BasicProgressLoggingAwareFormatterTest {
    private static final String EOL = String.format('%n')
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final StandardOutputListener infoMessage = context.mock(StandardOutputListener.class)
    private final StandardOutputListener errorMessage = context.mock(StandardOutputListener.class)
    private final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()
    private final BasicProgressLoggingAwareFormatter formatter = new BasicProgressLoggingAwareFormatter(loggerContext, infoMessage, errorMessage)

    @Test
    public void logsEventWithMessage() {
        context.checking {
            one(infoMessage).onOutput(String.format('message%n'))
        }

        formatter.format(event('message'))
    }

    @Test
    public void logsEventWithMessageAndException() {
        context.checking {
            one(infoMessage).onOutput(withParam(allOf(startsWith(String.format('message%n')), containsString('java.lang.RuntimeException: broken'))))
        }

        formatter.format(event('message', new RuntimeException('broken')))
    }

    @Test
    public void logsEventWithErrorMessage() {
        context.checking {
            one(errorMessage).onOutput(String.format('message%n'))
        }

        formatter.format(event('message', Level.ERROR))
    }

    @Test
    public void logsProgressMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(' ')
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void ignoresProgressStatusMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(' ')
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsNestedProgressMessages() {
        context.checking {
            one(infoMessage).onOutput('description1')
            one(infoMessage).onOutput(EOL)
            one(infoMessage).onOutput('description2')
            one(infoMessage).onOutput(' ')
            one(infoMessage).onOutput('complete2')
            one(infoMessage).onOutput(EOL)
            one(infoMessage).onOutput('complete1')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description1', Logging.PROGRESS_STARTED))
        formatter.format(event('description2', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete2', Logging.PROGRESS_COMPLETE))
        formatter.format(event('complete1', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsMixOfProgressAndInfoMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
            one(infoMessage).onOutput(String.format('message%n'))
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('message'))
        formatter.format(event('tick', Logging.PROGRESS))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsMixOfProgressAndErrorMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
            one(errorMessage).onOutput(String.format('message%n'))
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('message', Level.ERROR))
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatus() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatusAndOtherMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
            one(infoMessage).onOutput(String.format('message%n'))
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))
        formatter.format(event('message'))
        formatter.format(event('', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressMessagesWithNoStartStatus() {
        formatter.format(event('', Logging.PROGRESS_STARTED))

        context.checking {
            one(infoMessage).onOutput('done')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('done', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsNestedProgressMessagesWithNoStartStatusAndOtherMessages() {
        context.checking {
            one(infoMessage).onOutput('outer')
        }

        formatter.format(event('outer', Logging.PROGRESS_STARTED))

        formatter.format(event('', Logging.PROGRESS_STARTED))

        context.checking {
            one(infoMessage).onOutput(EOL)
            one(errorMessage).onOutput(String.format('message%n'))
        }

        formatter.format(event('message', Level.ERROR))

        context.checking {
            one(infoMessage).onOutput('done inner')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('done inner', Logging.PROGRESS_COMPLETE))

        context.checking {
            one(infoMessage).onOutput('done outer')
            one(infoMessage).onOutput(EOL)
        }

        formatter.format(event('done outer', Logging.PROGRESS_COMPLETE))
    }

    private ILoggingEvent event(String text, Marker marker) {
        event(text, null, marker)
    }

    private ILoggingEvent event(String text, Level level) {
        event(text, null, null, level)
    }

    private ILoggingEvent event(String text, Throwable failure = null, marker = null, Level level = Level.INFO) {
        IThrowableProxy throwableProxy = failure == null ? null : new ThrowableProxy(failure)
        [
                getLevel: {level},
                getThrowableProxy: {throwableProxy},
                getFormattedMessage: {text},
                getMarker: {marker}
        ] as ILoggingEvent
    }
}
