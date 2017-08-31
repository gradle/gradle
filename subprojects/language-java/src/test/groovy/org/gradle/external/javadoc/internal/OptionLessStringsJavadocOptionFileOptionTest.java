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

import com.google.common.collect.Lists;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class OptionLessStringsJavadocOptionFileOptionTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private OptionLessStringsJavadocOptionFileOption optionLessStringsOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        optionLessStringsOption = new OptionLessStringsJavadocOptionFileOption(Lists.<String>newArrayList());
    }

    @Test
    public void writeNullValue() throws IOException {
        final String firstValue = "firstValue";
        final String secondValue = "secondValue";

        context.checking(new Expectations() {{
            oneOf(writerContextMock).write(firstValue);
            oneOf(writerContextMock).newLine();
            oneOf(writerContextMock).write(secondValue);
            oneOf(writerContextMock).newLine();
        }});

        optionLessStringsOption.write(writerContextMock);
    }

}
