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
package org.gradle.api.tasks.scala;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
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

public class ScalaCompileTest extends AbstractCompileTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ScalaCompile scalaCompile;

    private Compiler<ScalaJavaJointCompileSpec> scalaCompiler;
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private FileCollection scalaClasspath;

    @Override
    public AbstractCompile getCompile() {
        return scalaCompile;
    }

    @Override
    public ConventionTask getTask() {
        return scalaCompile;
    }

    @Before
    public void setUp() {
        scalaCompile = createTask(ScalaCompile.class);
        scalaCompiler = context.mock(Compiler.class);
        scalaCompile.setCompiler(scalaCompiler);

        GFileUtils.touch(new File(srcDir, "incl/file.scala"));
        GFileUtils.touch(new File(srcDir, "incl/file.java"));
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(false));
            one(scalaCompiler).execute((ScalaJavaJointCompileSpec) with(IsNull.notNullValue()));
        }});

        scalaCompile.execute();
    }

    @Test
    public void testMoansIfScalaClasspathIsEmpty() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(TaskExecutionException.class);
        thrown.expectCause(new CauseMatcher(InvalidUserDataException.class, "'testTask.scalaClasspath' must not be empty"));

        scalaCompile.execute();
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        scalaClasspath = context.mock(FileTreeInternal.class);
        compile.setScalaClasspath(scalaClasspath);
        final FileTree classpath = context.mock(FileTreeInternal.class);
        final FileTree zincClasspath = context.mock(FileTreeInternal.class);

        context.checking(new Expectations(){{
            allowing(scalaClasspath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(classpath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(zincClasspath).getFiles(); will(returnValue(new HashSet<File>()));
        }});
        compile.setClasspath(classpath);
        compile.setZincClasspath(zincClasspath);
        compile.getScalaCompileOptions().getIncrementalOptions().setAnalysisFile(new File("analysisFile"));
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
