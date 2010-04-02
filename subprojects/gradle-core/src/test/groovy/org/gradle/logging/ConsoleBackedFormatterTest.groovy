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
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import org.gradle.api.logging.Logging
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Marker
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import org.junit.Before
import ch.qos.logback.classic.Level

@RunWith(JMock.class)
class ConsoleBackedFormatterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Console console = context.mock(Console.class)
    private final TextArea mainArea = context.mock(TextArea.class)
    private final ConsoleBackedFormatter formatter = new ConsoleBackedFormatter((LoggerContext) LoggerFactory.getILoggerFactory(), console)

    @Before
    public void setup() {
        context.checking {
            allowing(console).getMainArea()
            will(returnValue(mainArea))
        }
    }

    @Test
    public void logsEventWithMessage() {
        context.checking {
            one(mainArea).append(String.format('message%n'))
        }

        formatter.format(event('message'))
    }

    @Test
    public void logsEventWithMessageAndException() {
        context.checking {
            one(mainArea).append(withParam(anything()))
            will { message ->
                assertThat(message.toString(), startsWith(String.format('message%n')))
                assertThat(message.toString(), containsString('java.lang.RuntimeException: broken'))
            }
        }

        formatter.format(event('message', new RuntimeException('broken')))
    }

    @Test
    public void logsErrorMessage() {
        context.checking {
            one(mainArea).append(String.format('message%n'))
        }

        formatter.format(event('message', Level.ERROR))
    }

    @Test
    public void logsProgressMessages() {
        Label statusBar = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            one(statusBar).setText('description')
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))

        context.checking {
            one(statusBar).close()
            one(mainArea).append(String.format('description complete%n'))
        }
        
        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressStatusMessages() {
        Label statusBar = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            one(statusBar).setText('description')
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))

        context.checking {
            one(statusBar).setText('description status')
        }

        formatter.format(event('status', Logging.PROGRESS))
    }

    @Test
    public void logsNestedProgressMessages() {
        Label statusBar1 = statusBar()
        Label statusBar2 = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar1))
            one(statusBar1).setText('description1')
        }

        formatter.format(event('description1', Logging.PROGRESS_STARTED))

        context.checking {
            one(mainArea).append(String.format('description1%n'))
            one(console).addStatusBar()
            will(returnValue(statusBar2))
            one(statusBar2).setText('description2')
        }

        formatter.format(event('description2', Logging.PROGRESS_STARTED))

        context.checking {
            one(statusBar2).setText('description2 tick')
        }

        formatter.format(event('tick', Logging.PROGRESS))

        context.checking {
            one(statusBar2).close()
            one(mainArea).append(String.format('description2 complete2%n'))
        }

        formatter.format(event('complete2', Logging.PROGRESS_COMPLETE))

        context.checking {
            one(statusBar1).close()
            one(mainArea).append(String.format('description1 complete1%n'))
        }

        formatter.format(event('complete1', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsMixOfProgressAndOtherMessages() {
        Label statusBar = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            one(statusBar).setText('description')
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(mainArea).append(String.format('message%n'))
        }

        formatter.format(event('message'))

        context.checking {
            one(statusBar).close()
            one(mainArea).append(String.format('description complete%n'))
        }

        formatter.format(event('complete', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatus() {
        Label statusBar = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            one(statusBar).setText('description')
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(statusBar).close()
        }

        formatter.format(event('', Logging.PROGRESS_COMPLETE))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatusAndOtherMessages() {
        Label statusBar = statusBar()

        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            one(statusBar).setText('description')
        }

        formatter.format(event('description', Logging.PROGRESS_STARTED))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(mainArea).append(String.format('message%n'))
        }

        formatter.format(event('message'))

        context.checking {
            one(statusBar).close()
        }

        formatter.format(event('', Logging.PROGRESS_COMPLETE))
    }

    private Label statusBar() {
        return context.mock(Label.class)
    }

    private ILoggingEvent event(String text, Marker marker) {
        event(text, null, marker)
    }

    private ILoggingEvent event(String text, Level level) {
        event(text, null, null, level)
    }

    private ILoggingEvent event(String text, Throwable failure = null, marker = null, Level level = Level.INFO) {
        IThrowableProxy throwableProxy = failure == null ? null : new ThrowableProxy(failure)
        [getLevel: {level}, getThrowableProxy: {throwableProxy}, getFormattedMessage: {text}, getMarker: {marker}] as ILoggingEvent
    }
}
