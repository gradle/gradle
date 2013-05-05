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
package org.gradle.util;

import org.gradle.api.Action;
import org.gradle.internal.SystemProperties;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(JMock.class)
public class LineBufferingOutputStreamTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private Action<String> action = context.mock(Action.class);
    private LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);
    private String eol;

    @Before
    public void setUp() {
        eol = SystemProperties.getLineSeparator();
    }

    @After
    public void tearDown() {
        System.setProperty("line.separator", eol);
    }

    @Test
    public void logsEachLineAsASeparateLogMessage() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute(TextUtil.toPlatformLineSeparators("line 1\n"));
            one(action).execute(TextUtil.toPlatformLineSeparators("line 2\n"));
        }});

        outputStream.write(TextUtil.toPlatformLineSeparators("line 1\nline 2\n").getBytes());
    }

    @Test
    public void logsEmptyLines() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute(TextUtil.getPlatformLineSeparator());
            one(action).execute(TextUtil.getPlatformLineSeparator());
        }});

        outputStream.write(TextUtil.toPlatformLineSeparators("\n\n").getBytes());
    }

    @Test
    public void handlesSingleCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute("line 1-");
            one(action).execute("line 2-");
        }});

        System.setProperty("line.separator", "-");
        outputStream = new LineBufferingOutputStream(action, 8);

        outputStream.write(String.format("line 1-line 2-").getBytes());
    }
    
    @Test
    public void handlesMultiCharacterLineSeparator() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute("line 1----");
            one(action).execute("line 2----");
        }});

        System.setProperty("line.separator", "----");
        outputStream = new LineBufferingOutputStream(action, 8);

        outputStream.write(String.format("line 1----line 2----").getBytes());
    }

    @Test
    public void logsLineWhichIsLongerThanInitialBufferLength() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute(TextUtil.toPlatformLineSeparators("a line longer than 8 bytes long\n"));
            one(action).execute("line 2");
        }});
        outputStream.write(TextUtil.toPlatformLineSeparators("a line longer than 8 bytes long\n").getBytes());
        outputStream.write("line 2".getBytes());
        outputStream.close();
    }

    @Test
    public void logsPartialLineOnFlush() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute("line 1");
        }});

        outputStream.write("line 1".getBytes());
        outputStream.flush();
    }

    @Test
    public void logsPartialLineOnClose() throws IOException {
        context.checking(new Expectations() {{
            one(action).execute("line 1");
        }});
        outputStream.write("line 1".getBytes());
        outputStream.close();
    }
    
    @Test(expected = IOException.class)
    public void cannotWriteAfterClose() throws IOException {
        outputStream.close();
        outputStream.write("ignore me".getBytes());
    }
}
