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
import org.gradle.util.GFileUtils;
import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class CompileTest extends AbstractCompileTest {
    private Compile compile;

    private AntJavac antCompileMock;

    private Mockery context = new Mockery();

    @Before public void setUp()  {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        compile = createTask(Compile.class);
        antCompileMock = context.mock(AntJavac.class);
        compile.antCompile = antCompileMock;

        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }
           
    public ConventionTask getTask() {
        return compile;
    }

    public void testExecute(final int numFilesCompiled) {
        setUpMocksAndAttributes(compile);
        context.checking(new Expectations() {{
            one(antCompileMock).execute(
                    with(hasSameItems(compile.getFilteredSrc())),
                    with(equalTo(compile.getDestinationDir())),
                    with(equalTo(compile.getDependencyCacheDir())),
                    with(equalTo(compile.getClasspath())),
                    with(equalTo(compile.getSourceCompatibility())),
                    with(equalTo(compile.getTargetCompatibility())),
                    with(equalTo(compile.getOptions())),
                    with(equalTo(compile.getAnt())));
            one(antCompileMock).getNumFilesCompiled(); will(returnValue(numFilesCompiled));
        }});
        compile.execute();
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
