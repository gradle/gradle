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

import org.gradle.internal.SystemProperties;
import org.gradle.internal.io.LineBufferingOutputStream;
import org.gradle.internal.io.TextStream;
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
    private TextStream action = context.mock(TextStream.class);
    private String eol;

    @Before
    public void setUp() {
        eol = SystemProperties.getInstance().getLineSeparator();
    }

    @After
    public void tearDown() {
        System.setProperty("line.separator", eol);
    }

    @Test
    public void logsEachLineAsASeparateLogMessage() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text(TextUtil.toPlatformLineSeparators("line 1\n"));
            one(action).text(TextUtil.toPlatformLineSeparators("line 2\n"));
        }});

        outputStream.write(TextUtil.toPlatformLineSeparators("line 1\nline 2\n").getBytes());
    }

    @Test
    public void buffersTextUntilEndOfLineReached() throws IOException {
        System.setProperty("line.separator", "-");
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1-");
        }});

        outputStream.write("line ".getBytes());
        outputStream.write("1-line 2".getBytes());

        context.checking(new Expectations() {{
            one(action).text("line 2-");
        }});

        outputStream.write("-".getBytes());
    }

    @Test
    public void logsEmptyLines() throws IOException {
        System.setProperty("line.separator", "-");
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("-");
            one(action).text("-");
        }});

        outputStream.write("--".getBytes());
    }

    @Test
    public void handlesSingleCharacterLineSeparator() throws IOException {
        System.setProperty("line.separator", "-");
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1-");
            one(action).text("line 2-");
        }});

        outputStream.write(String.format("line 1-line 2-").getBytes());
    }

    @Test
    public void handlesMultiCharacterLineSeparator() throws IOException {
        final String separator = new String(new byte[]{'\r', '\n'});
        System.setProperty("line.separator", separator);
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1" + separator);
            one(action).text("line 2" + separator);
        }});

        outputStream.write(("line 1" + separator + "line 2" + separator).getBytes());
    }

    @Test
    public void logsLineWhichIsLongerThanInitialBufferLength() throws IOException {
        System.setProperty("line.separator", "-");
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("a line longer than 8 bytes long-");
            one(action).text("line 2");
        }});
        outputStream.write("a line longer than 8 bytes long-".getBytes());
        outputStream.write("line 2".getBytes());
        outputStream.flush();
    }

    @Test
    public void logsPartialLineOnFlush() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1");
        }});

        outputStream.write("line 1".getBytes());
        outputStream.flush();
    }

    @Test
    public void logsNothingOnCloseWhenNothingHasBeenWrittenToStream() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).endOfStream(null);
        }});
        outputStream.close();
    }

    @Test
    public void logsNothingOnCloseWhenCompleteLineHasBeenWrittenToStream() throws IOException {
        System.setProperty("line.separator", "-");
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1-");
            one(action).endOfStream(null);
        }});

        outputStream.write("line 1-".getBytes());
        outputStream.close();
    }

    @Test
    public void logsPartialLineOnClose() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).text("line 1");
            one(action).endOfStream(null);
        }});
        outputStream.write("line 1".getBytes());
        outputStream.close();
    }

    @Test(expected = IOException.class)
    public void cannotWriteAfterClose() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8);

        context.checking(new Expectations() {{
            one(action).endOfStream(null);
        }});
        outputStream.close();
        outputStream.write("ignore me".getBytes());
    }

    @Test
    public void splitsLongLines() throws IOException {
        LineBufferingOutputStream outputStream = new LineBufferingOutputStream(action, 8, 13);
        context.checking(new Expectations() {{
            one(action).text("1234567890123");
            one(action).text("4567890123456");
            one(action).text("789");
            one(action).endOfStream(null);
        }});
        outputStream.write("12345678901234567890123456789".getBytes());
        outputStream.close();
    }
}
