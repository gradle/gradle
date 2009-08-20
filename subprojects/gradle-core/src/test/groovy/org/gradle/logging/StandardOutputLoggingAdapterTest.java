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

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(JMock.class)
public class StandardOutputLoggingAdapterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private Logger logger;
    private StandardOutputLoggingAdapter adapter;
    private String eol;

    @Before
    public void setup() {
        eol = System.getProperty("line.separator");

        context.setImposteriser(ClassImposteriser.INSTANCE);
        logger = context.mock(Logger.class);
        adapter = new StandardOutputLoggingAdapter(logger, LogLevel.ERROR, 8);
    }

    @After
    public void teardown() {
        System.setProperty("line.separator", eol);
    }

    @Test
    public void logsEachLineAsASeparateLogMessage() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "line 1");
            one(logger).log(LogLevel.ERROR, "line 2");
        }});

        adapter.write(String.format("line 1%nline 2%n").getBytes());
    }

    @Test
    public void logsEmptyLines() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "");
            one(logger).log(LogLevel.ERROR, "");
        }});

        adapter.write(String.format("%n%n").getBytes());
    }

    @Test
    public void handlesSingleCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "line 1");
            one(logger).log(LogLevel.ERROR, "line 2");
        }});

        System.setProperty("line.separator", "-");
        adapter = new StandardOutputLoggingAdapter(logger, LogLevel.ERROR);

        adapter.write(String.format("line 1-line 2-").getBytes());
    }
    
    @Test
    public void handlesMultiCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "line 1");
            one(logger).log(LogLevel.ERROR, "line 2");
        }});

        System.setProperty("line.separator", "----");
        adapter = new StandardOutputLoggingAdapter(logger, LogLevel.ERROR);

        adapter.write(String.format("line 1----line 2----").getBytes());
    }

    @Test
    public void logsLineWhichIsLongerThanInitialBufferLength() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "a line longer than 8 bytes long");
            one(logger).log(LogLevel.ERROR, "line 2");
        }});
        adapter.write(String.format("a line longer than 8 bytes long%n").getBytes());
        adapter.write("line 2".getBytes());
        adapter.close();
    }

    @Test
    public void doesNotLogPartialLineOnFlush() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "line 1");
        }});

        adapter.write("line 1".getBytes());
        adapter.flush();
    }

    @Test
    public void logsPartialLineOnClose() throws IOException {
        context.checking(new Expectations() {{
            one(logger).log(LogLevel.ERROR, "line 1");
        }});
        adapter.write("line 1".getBytes());
        adapter.close();
    }
    
    @Test(expected = IOException.class)
    public void cannotWriteAfterClose() throws IOException {
        adapter.close();
        adapter.write("ignore me".getBytes());
    }
}
