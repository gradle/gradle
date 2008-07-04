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

import org.gradle.api.DependencyManager;
import org.gradle.api.Task;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GUtil;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;

/**
 * @author Hans Dockter
 */
public class CompileTest extends AbstractCompileTest {
    private Compile compile;

    private AntJavac antCompileMock;

    private Mockery context = new Mockery();

    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        compile = new Compile(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        compile.getProject().setRootDir(AbstractCompileTest.TEST_ROOT_DIR);
        antCompileMock = context.mock(AntJavac.class);
        compile.antCompile = antCompileMock;
    }
           
    public Task getTask() {
        return compile;
    }

    public void testExecute() {
        setUpMocksAndAttributes(compile);
        context.checking(new Expectations() {{
            one(antCompileMock).execute(compile.getSrcDirs(), compile.getIncludes(), compile.getExcludes(), compile.getDestinationDir(),
                    GUtil.addLists(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH, AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH),
                    compile.getSourceCompatibility(), compile.getTargetCompatibility(), compile.getOptions(), compile.getProject().getAnt());
        }});

        compile.execute();
        context.assertIsSatisfied();
    }

    // todo We need to do this to make the compiler happy. We need to file a Jira to Groovy.
    public Compile getCompile() {
        return compile;
    }
}
