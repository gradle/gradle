/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec;
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

public class GosuCompileTest extends AbstractCompileTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private GosuCompile gosuCompile;

    private org.gradle.language.base.internal.compile.Compiler<GosuCompileSpec> gosuCompiler;
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private FileTreeInternal gosuClasspath;

    @Override
    public AbstractCompile getCompile() {
        return gosuCompile;
    }

    @Override
    public ConventionTask getTask() {
        return gosuCompile;
    }

    @Before
    public void setUp() {
        gosuCompile = createTask(GosuCompile.class);
        gosuCompiler = context.mock(Compiler.class);
        gosuCompile.setCompiler(gosuCompiler);

        GFileUtils.touch(new File(srcDir, "incl/file.gs"));
        GFileUtils.touch(new File(srcDir, "incl/file.java")); //TODO delete me
    }

    @Test
    public void testExecuteDoingWork() {
        setUpMocksAndAttributes(gosuCompile);
        context.checking(new Expectations() {{
            allowing(gosuClasspath).isEmpty(); will(returnValue(false));
            one(gosuCompiler).execute((GosuCompileSpec) with(IsNull.notNullValue()));
        }});

        gosuCompile.execute();
    }

    @Test
    public void testMoansIfGosuClasspathIsEmpty() {
        setUpMocksAndAttributes(gosuCompile);
        context.checking(new Expectations() {{
            allowing(gosuClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(TaskExecutionException.class);
        thrown.expectCause(new CauseMatcher(InvalidUserDataException.class, "'testTask.gosuClasspath' must not be empty"));

        gosuCompile.execute();
    }

    protected void setUpMocksAndAttributes(final GosuCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.8");
        compile.setTargetCompatibility("1.8");
        compile.setDestinationDir(destDir);
        gosuClasspath = context.mock(FileTreeInternal.class);
        compile.setGosuClasspath(gosuClasspath);
        final FileTreeInternal classpath = context.mock(FileTreeInternal.class);

        context.checking(new Expectations(){{
            allowing(gosuClasspath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(gosuClasspath).visit((FileVisitor) with(anything()));
            allowing(gosuClasspath).visitTreeOrBackingFile((FileVisitor) with(anything()));
            allowing(classpath).getFiles(); will(returnValue(new HashSet<File>()));
            allowing(classpath).visit((FileVisitor) with(anything()));
            allowing(classpath).visitTreeOrBackingFile((FileVisitor) with(anything()));
        }});
        compile.setClasspath(classpath);
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
