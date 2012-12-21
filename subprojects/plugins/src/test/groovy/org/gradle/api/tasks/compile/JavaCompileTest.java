/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavaCompileTest extends AbstractCompileTest {
    private JavaCompile compile;

    private Compiler<JavaCompileSpec> compilerMock;

    private Mockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp()  {
        compile = createTask(JavaCompile.class);
        compilerMock = context.mock(Compiler.class);
        compile.setJavaCompiler(compilerMock);

        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }
           
    public ConventionTask getTask() {
        return compile;
    }

    public void testExecute(final int numFilesCompiled) {
        setUpMocksAndAttributes(compile);
        context.checking(new Expectations() {{
            WorkResult result = context.mock(WorkResult.class);

            one(compilerMock).execute((JavaCompileSpec) with(Matchers.notNullValue()));
            will(returnValue(result));
            allowing(result).getDidWork();
            will(returnValue(numFilesCompiled > 0));
        }});
        compile.compile();
    }

    @Test
    public void testExecuteDoingWork() {
        testExecute(7);
        assertTrue(compile.getDidWork());
    }

    @Test
    public void testExecuteNotDoingWork() {
        testExecute(0);
        assertFalse(compile.getDidWork());
    }

    public JavaCompile getCompile() {
        return compile;
    }
}
