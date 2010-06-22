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
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.GFileUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.gradle.util.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class CompileTest extends AbstractCompileTest {
    private Compile compile;

    private JavaCompiler compilerMock;

    private Mockery context = new Mockery();

    @Before public void setUp()  {
        super.setUp();
        compile = createTask(Compile.class);
        compilerMock = context.mock(JavaCompiler.class);
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

            one(compilerMock).setSource(with(hasSameItems(compile.getSource())));
            one(compilerMock).setClasspath(compile.getClasspath());
            one(compilerMock).setDestinationDir(compile.getDestinationDir());
            one(compilerMock).setDependencyCacheDir(compile.getDependencyCacheDir());
            one(compilerMock).setSourceCompatibility(compile.getSourceCompatibility());
            one(compilerMock).setTargetCompatibility(compile.getTargetCompatibility());
            one(compilerMock).execute();
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

    // todo We need to do this to make the compiler happy. We need to file a Jira to Groovy.
    public Compile getCompile() {
        return compile;
    }
}
