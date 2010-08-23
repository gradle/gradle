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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener
import org.gradle.logging.internal.LogEvent
import org.gradle.logging.internal.ProgressCompleteEvent
import org.gradle.logging.internal.ProgressEvent
import org.gradle.logging.internal.ProgressStartEvent
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import org.gradle.logging.internal.LogLevelChangeEvent

@RunWith(JMock.class)
class BasicProgressLoggingAwareFormatterTest {
    private static final String EOL = String.format('%n')
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final StandardOutputListener infoMessage = context.mock(StandardOutputListener.class)
    private final StandardOutputListener errorMessage = context.mock(StandardOutputListener.class)
    private final BasicProgressLoggingAwareFormatter formatter = new BasicProgressLoggingAwareFormatter(infoMessage, errorMessage)

    @Test
    public void logsEventWithMessage() {
        context.checking {
            one(infoMessage).onOutput(String.format('message%n'))
        }

        formatter.onOutput(event('message'))
    }

    @Test
    public void logsEventWithMessageAndException() {
        context.checking {
            one(infoMessage).onOutput(withParam(allOf(startsWith(String.format('message%n')), containsString('java.lang.RuntimeException: broken'))))
        }

        formatter.onOutput(event('message', new RuntimeException('broken')))
    }

    @Test
    public void logsDebugEventWithMessage() {
        context.checking {
            one(infoMessage).onOutput(String.format('[INFO] [category] message%n'))
        }

        formatter.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        formatter.onOutput(event('message'))
    }

    @Test
    public void logsEventWithErrorMessage() {
        context.checking {
            one(errorMessage).onOutput(String.format('message%n'))
        }

        formatter.onOutput(event('message', LogLevel.ERROR))
    }

    @Test
    public void logsProgressMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(' ')
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(start('description'))
        formatter.onOutput(complete('complete'))
    }

    @Test
    public void ignoresProgressStatusMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(' ')
            one(infoMessage).onOutput('complete')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(start('description'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete'))
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

        formatter.onOutput(start('description1'))
        formatter.onOutput(start('description2'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete2'))
        formatter.onOutput(complete('complete1'))
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

        formatter.onOutput(start('description'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(event('message'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete'))
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

        formatter.onOutput(start('description'))
        formatter.onOutput(event('message', LogLevel.ERROR))
        formatter.onOutput(complete('complete'))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatus() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(start('description'))
        formatter.onOutput(complete(''))
    }

    @Test
    public void logsProgressMessagesWithNoCompletionStatusAndOtherMessages() {
        context.checking {
            one(infoMessage).onOutput('description')
            one(infoMessage).onOutput(EOL)
            one(infoMessage).onOutput(String.format('message%n'))
        }

        formatter.onOutput(start('description'))
        formatter.onOutput(event('message'))
        formatter.onOutput(complete(''))
    }

    @Test
    public void logsProgressMessagesWithNoStartStatus() {
        formatter.onOutput(start(''))

        context.checking {
            one(infoMessage).onOutput('done')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(complete('done'))
    }

    @Test
    public void logsNestedProgressMessagesWithNoStartStatusAndOtherMessages() {
        context.checking {
            one(infoMessage).onOutput('outer')
        }

        formatter.onOutput(start('outer'))

        formatter.onOutput(start(''))

        context.checking {
            one(infoMessage).onOutput(EOL)
            one(errorMessage).onOutput(String.format('message%n'))
        }

        formatter.onOutput(event('message', LogLevel.ERROR))

        context.checking {
            one(infoMessage).onOutput('done inner')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(complete('done inner'))

        context.checking {
            one(infoMessage).onOutput('done outer')
            one(infoMessage).onOutput(EOL)
        }

        formatter.onOutput(complete('done outer'))
    }

    private LogEvent event(String text) {
        return new LogEvent('category', LogLevel.INFO, text)
    }

    private LogEvent event(String text, LogLevel logLevel) {
        return new LogEvent('category', logLevel, text)
    }

    private LogEvent event(String text, Throwable throwable) {
        return new LogEvent('category', LogLevel.INFO, text, throwable)
    }

    private ProgressStartEvent start(String description) {
        return new ProgressStartEvent('category', description)
    }

    private ProgressEvent progress(String status) {
        return new ProgressEvent('category', status)
    }

    private ProgressCompleteEvent complete(String status) {
        return new ProgressCompleteEvent('category', status)
    }
}
