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

import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

import org.junit.Before

import org.gradle.logging.internal.LogEvent
import org.gradle.api.logging.LogLevel
import org.gradle.logging.internal.ProgressStartEvent
import org.gradle.logging.internal.ProgressEvent
import org.gradle.logging.internal.ProgressCompleteEvent

@RunWith(JMock.class)
class ConsoleBackedFormatterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Console console = context.mock(Console.class)
    private final TextArea mainArea = context.mock(TextArea.class)
    private final Label statusBar = context.mock(Label.class)
    private ConsoleBackedFormatter formatter

    @Before
    public void setup() {
        context.checking {
            one(console).addStatusBar()
            will(returnValue(statusBar))
            allowing(console).getMainArea()
            will(returnValue(mainArea))
        }

        formatter = new ConsoleBackedFormatter(console)
    }

    @Test
    public void logsEventWithMessage() {
        context.checking {
            one(mainArea).append(String.format('message%n'))
        }

        formatter.onOutput(event('message'))
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

        formatter.onOutput(event('message', new RuntimeException('broken')))
    }

    @Test
    public void logsErrorMessage() {
        context.checking {
            one(mainArea).append(String.format('message%n'))
        }

        formatter.onOutput(event('message', LogLevel.ERROR))
    }

    @Test
    public void logsProgressMessages() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description'))

        context.checking {
            one(statusBar).setText('')
            one(mainArea).append(String.format('description complete%n'))
        }
        
        formatter.onOutput(complete('complete'))
    }

    @Test
    public void logsProgressStatusMessages() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description'))

        context.checking {
            one(statusBar).setText('> status')
        }

        formatter.onOutput(progress('status'))
    }

    @Test
    public void logsNestedProgressMessages() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description1'))

        context.checking {
            one(mainArea).append(String.format('description1%n'))
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description2'))

        context.checking {
            one(statusBar).setText('> tick')
        }

        formatter.onOutput(progress('tick'))

        context.checking {
            one(statusBar).setText('')
            one(mainArea).append(String.format('description2 complete2%n'))
        }

        formatter.onOutput(complete('complete2'))

        context.checking {
            one(statusBar).setText('')
            one(mainArea).append(String.format('description1 complete1%n'))
        }

        formatter.onOutput(complete('complete1'))
    }

    @Test
    public void logsMixOfProgressAndOtherMessages() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description'))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(mainArea).append(String.format('message%n'))
        }

        formatter.onOutput(event('message'))

        context.checking {
            one(statusBar).setText('')
            one(mainArea).append(String.format('description complete%n'))
        }

        formatter.onOutput(complete('complete'))
    }

    @Test
    public void logsProgressMessagesWithEmptyCompletionStatus() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description'))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(statusBar).setText('')
        }

        formatter.onOutput(complete(''))
    }

    @Test
    public void logsProgressMessagesWithEmptyCompletionStatusAndOtherMessages() {

        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start('description'))

        context.checking {
            one(mainArea).append(String.format('description%n'))
            one(mainArea).append(String.format('message%n'))
        }

        formatter.onOutput(event('message'))

        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(complete(''))
    }

    @Test
    public void logsProgressMessagesWithEmptyStartAndCompletionStatus() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start(''))

        context.checking {
            one(statusBar).setText('> running')
        }
        formatter.onOutput(progress('running'))

        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(complete(''))
    }

    @Test
    public void logsProgressMessagesWithEmptyStartAndCompletionStatusAndOtherMessages() {
        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(start(''))

        context.checking {
            one(mainArea).append(String.format('message%n'))
        }

        formatter.onOutput(event('message'))

        context.checking {
            one(statusBar).setText('')
        }

        formatter.onOutput(complete(''))
    }
    
    private Label statusBar() {
        return context.mock(Label.class)
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
