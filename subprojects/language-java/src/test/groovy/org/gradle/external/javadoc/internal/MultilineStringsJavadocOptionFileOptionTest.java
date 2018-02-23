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

package org.gradle.external.javadoc.internal;

import com.google.common.collect.Lists;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MultilineStringsJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private MultilineStringsJavadocOptionFileOption linksOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);
        linksOption = new MultilineStringsJavadocOptionFileOption(optionName, Lists.<String>newArrayList());
    }

    @Test
    public void writeNullValue() throws IOException {
        linksOption.writeCollectionValue(writerContextMock);
    }

    @Test
    public void writeNonNullValue() throws IOException {
        final String extDocUrl = "extDocUrl";

        linksOption.getValue().add(extDocUrl);
        context.checking(new Expectations() {{
            final List<String> tempList = new ArrayList<String>();
            tempList.add(extDocUrl);
            oneOf(writerContextMock).writeMultilineValuesOption(optionName, tempList);
        }});

        linksOption.writeCollectionValue(writerContextMock);
    }

    @Test
    public void writeMultipleValues() throws IOException {
        final List<String> tempList = new ArrayList<String>();
        final String docUrl1 = "docUrl1";
        final String docUrl2 = "docUrl2";

        linksOption.getValue().add(docUrl1);
        linksOption.getValue().add(docUrl2);
        context.checking(new Expectations() {{
            tempList.add(docUrl1);
            tempList.add(docUrl2);
            oneOf(writerContextMock).writeMultilineValuesOption(optionName, tempList);
        }});

        linksOption.writeCollectionValue(writerContextMock);
    }
}
