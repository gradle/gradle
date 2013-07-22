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

import org.gradle.external.javadoc.JavadocOptionFileOption;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavadocOptionFileTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    @SuppressWarnings("unchecked")
    private JavadocOptionFileOption<String> optionFileOptionMock = context.mock(JavadocOptionFileOption.class);
    private final String optionName = "testOption";
    private JavadocOptionFile optionFile = new JavadocOptionFile();

    @Test
    public void testDefaults() {
        assertNotNull(optionFile.getOptions());
        assertTrue(optionFile.getOptions().isEmpty());

        assertNotNull(optionFile.getSourceNames());
        assertNotNull(optionFile.getSourceNames().getValue());
        assertTrue(optionFile.getSourceNames().getValue().isEmpty());
    }

    @Test
    public void testAddOption() {
        context.checking(new Expectations() {{
            one(optionFileOptionMock).getOption();
            returnValue(optionName);
        }});

        optionFile.addOption(optionFileOptionMock);
    }
}
