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
package org.gradle.api.tasks.mirah;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.mirah.MirahJavaJointCompileSpec;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.core.IsNull;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.util.HashSet;

public class MirahCompileTest extends AbstractCompileTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private MirahCompile mirahCompile;

    private Compiler<MirahJavaJointCompileSpec> mirahCompiler;
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private FileCollection mirahClasspath;

    @Override
    public AbstractCompile getCompile() {
        return mirahCompile;
    }

    @Override
    public ConventionTask getTask() {
        return mirahCompile;
    }

    @Before
    public void setUp() {
        mirahCompile = createTask(MirahCompile.class);
        mirahCompiler = context.mock(Compiler.class);
        mirahCompile.setCompiler(mirahCompiler);

        GFileUtils.touch(new File(srcDir, "incl/file.mirah"));
        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(mirahCompile);
        context.checking(new Expectations() {{
            allowing(mirahClasspath).isEmpty(); will(returnValue(false));
            one(mirahCompiler).execute((MirahJavaJointCompileSpec) with(IsNull.notNullValue()));
        }});

        mirahCompile.execute();
    }

    @Test
    public void testMoansIfMirahClasspathIsEmpty() {
        setUpMocksAndAttributes(mirahCompile);
        context.checking(new Expectations() {{
            allowing(mirahClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(TaskExecutionException.class);
        thrown.expectCause(new CauseMatcher(InvalidUserDataException.class, "'testTask.mirahClasspath' must not be empty"));

        mirahCompile.execute();
    }

    protected void setUpMocksAndAttributes(final MirahCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        mirahClasspath = context.mock(FileTree.class);
        compile.setMirahClasspath(mirahClasspath);
        final FileTree classpath = context.mock(FileTree.class);

        context.checking(new Expectations(){{
            allowing(mirahClasspath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(classpath).getFiles(); will(returnValue(new HashSet<File>()));
        }});
        compile.setClasspath(classpath);
        compile.getMirahCompileOptions().getIncrementalOptions().setAnalysisFile(new File("analysisFile"));
    }


    private class CauseMatcher<T extends Exception> extends BaseMatcher<T> {
        private final Class<T> throwableClass;
        private final String expectedMessage;

        public CauseMatcher(Class<T> throwableClass, String expectedMessage) {
            this.throwableClass = throwableClass;
            this.expectedMessage = expectedMessage;
        }

        public boolean matches(Object item) {
            return item.getClass().isAssignableFrom(throwableClass)
                        && ((T)item).getMessage().contains(expectedMessage);
        }

        public void describeTo(Description description) {

        }
    }
}
