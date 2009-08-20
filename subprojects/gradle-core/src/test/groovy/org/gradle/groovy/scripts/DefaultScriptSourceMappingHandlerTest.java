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
package org.gradle.groovy.scripts;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;

public class DefaultScriptSourceMappingHandlerTest
{
    private static final String TEST_CLASS_NAME_1 = "test class name 1";
    private static final File   TEST_SOURCE_FILE_1 = new File("test source file 1").getAbsoluteFile();
    private static final String TEST_CLASS_NAME_2 = "test class name 2";
    private static final File   TEST_SOURCE_FILE_2 = new File("test source file 2").getAbsoluteFile();

    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private File buildDir;

    @Before
    public void setUp() {
        buildDir = HelperUtil.makeNewTestDir();

    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @Test public void testAddAndGetSource() {
        final ScriptSource source1 = context.mock(ScriptSource.class, "scriptSource1");
        final ScriptSource source2 = context.mock(ScriptSource.class, "scriptSource2");

        context.checking(new Expectations() {{
            allowing(source1).getClassName();
            will(returnValue(TEST_CLASS_NAME_1));

            allowing(source1).getSourceFile();
            will(returnValue(TEST_SOURCE_FILE_1));

            allowing(source2).getClassName();
            will(returnValue(TEST_CLASS_NAME_2));

            allowing(source2).getSourceFile();
            will(returnValue(TEST_SOURCE_FILE_2));
        }});

        DefaultScriptSourceMappingHandler handler1 = new DefaultScriptSourceMappingHandler(buildDir);
        handler1.addSource(source1);
        assertThat(handler1.getSource(TEST_CLASS_NAME_1), equalTo(TEST_SOURCE_FILE_1));

        // make sure a second handler reads the saved mappings correctly
        DefaultScriptSourceMappingHandler handler2 = new DefaultScriptSourceMappingHandler(buildDir);
        assertThat(handler2.getSource(TEST_CLASS_NAME_1), equalTo(TEST_SOURCE_FILE_1));

        // Make sure changes to the second handler are seen in the first handler.
        handler2.addSource(source2);
        assertThat(handler1.getSource(TEST_CLASS_NAME_2), equalTo(TEST_SOURCE_FILE_2));
    }

    @Test public void testWithNonFileScriptSource() {
        final ScriptSource source = new StringScriptSource("string script source", "empty");

        DefaultScriptSourceMappingHandler handler = new DefaultScriptSourceMappingHandler(buildDir);
        handler.addSource(source);
        assertThat(handler.getSource(source.getClassName()), nullValue());
    }
}