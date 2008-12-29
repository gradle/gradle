/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RunWith(JMock.class)
public class StandardOutputLoggingAdapterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private Logger logger;
    private StandardOutputLoggingAdapter adapter;
    private Appender<LoggingEvent> appender;
    private String eol;

    @Before
    public void setup() {
        eol = System.getProperty("line.separator");

        context.setImposteriser(ClassImposteriser.INSTANCE);
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StandardOutputLoggingAdapterTest.class);
        appender = context.mock(Appender.class);
        logger.addAppender(appender);
        adapter = new StandardOutputLoggingAdapter(logger, Level.ERROR, 8);
    }

    @After
    public void teardown() {
        System.setProperty("line.separator", eol);
        logger.detachAppender(appender);
    }

    @Test
    public void logsEachLineAsASeparateLogMessage() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "line 1")));
            one(appender).doAppend(with(event(Level.ERROR, "line 2")));
        }});

        adapter.write(String.format("line 1%nline 2%n").getBytes());
    }

    @Test
    public void logsEmptyLines() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "")));
            one(appender).doAppend(with(event(Level.ERROR, "")));
        }});

        adapter.write(String.format("%n%n").getBytes());
    }

    @Test
    public void handlesSingleCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "line 1")));
            one(appender).doAppend(with(event(Level.ERROR, "line 2")));
        }});

        System.setProperty("line.separator", "-");
        adapter = new StandardOutputLoggingAdapter(logger, Level.ERROR);

        adapter.write(String.format("line 1-line 2-").getBytes());
    }
    
    @Test
    public void handlesMultiCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "line 1")));
            one(appender).doAppend(with(event(Level.ERROR, "line 2")));
        }});

        System.setProperty("line.separator", "----");
        adapter = new StandardOutputLoggingAdapter(logger, Level.ERROR);

        adapter.write(String.format("line 1----line 2----").getBytes());
    }

    @Test
    public void logsLineWhichIsLongerThanInitialBufferLength() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "a line longer than 8 bytes long")));
            one(appender).doAppend(with(event(Level.ERROR, "line 2")));
        }});
        adapter.write(String.format("a line longer than 8 bytes long%n").getBytes());
        adapter.write("line 2".getBytes());
        adapter.close();
    }

    @Test
    public void doesNotLogPartialLineOnFlush() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "line 1")));
        }});

        adapter.write("line 1".getBytes());
        adapter.flush();
    }

    @Test
    public void logsPartialLineOnClose() throws IOException {
        context.checking(new Expectations() {{
            one(appender).doAppend(with(event(Level.ERROR, "line 1")));
        }});
        adapter.write("line 1".getBytes());
        adapter.close();
    }

    @Test(expected = IOException.class)
    public void cannotWriteAfterClose() throws IOException {
        adapter.close();
        adapter.write("ignore me".getBytes());
    }

    private Matcher<LoggingEvent> event(final Level level, final String text) {
        return new BaseMatcher<LoggingEvent>() {

            public void describeTo(Description description) {
                description.appendText("equal LoggingEvent");
            }

            public boolean matches(Object o) {
                LoggingEvent event = (LoggingEvent) o;
                return event.getLevel().equals(level) && event.getMessage().equals(text);
            }
        };
    }
}
