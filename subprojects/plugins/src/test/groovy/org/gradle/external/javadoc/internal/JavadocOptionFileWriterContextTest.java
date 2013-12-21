/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.IOException;

public class JavadocOptionFileWriterContextTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private BufferedWriter bufferedWriterMock;

    private JavadocOptionFileWriterContext writerContext;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        bufferedWriterMock = context.mock(BufferedWriter.class);

        writerContext = new JavadocOptionFileWriterContext(bufferedWriterMock);
    }

    @Test
    public void testWrite() throws IOException {
        final String writeValue = "dummy";

        context.checking(new Expectations() {{
            one(bufferedWriterMock).write(writeValue);
        }});

        writerContext.write(writeValue);
    }

    @Test
    public void testNewLine() throws IOException {
        context.checking(new Expectations() {{
            one(bufferedWriterMock).newLine();
        }});

        writerContext.newLine();
    }

    @Test
    public void quotesAndEscapesOptionValue() throws IOException {
        context.checking(new Expectations(){{
            one(bufferedWriterMock).write("-");
            one(bufferedWriterMock).write("key");
            one(bufferedWriterMock).write(" ");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).write("1\\\\2\\\\");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).newLine();
        }});

        writerContext.writeValueOption("key", "1\\2\\");
    }

    @Test
    public void quotesAndEscapesOptionValues() throws IOException {
        context.checking(new Expectations(){{
            one(bufferedWriterMock).write("-");
            one(bufferedWriterMock).write("key");
            one(bufferedWriterMock).write(" ");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).write("a\\\\b:c");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).newLine();
        }});

        writerContext.writeValuesOption("key", WrapUtil.toList("a\\b", "c"), ":");
    }
}
