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
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.AbstractCompileTest;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.core.IsNull;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

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

        scalaCompile.compile();
    }

    @Test
    public void testMoansIfScalaClasspathIsEmpty() {
        setUpMocksAndAttributes(scalaCompile);
        context.checking(new Expectations() {{
            allowing(scalaClasspath).isEmpty(); will(returnValue(true));
        }});

        thrown.expect(InvalidUserDataException.class);
        thrown.expectMessage("foo");

        scalaCompile.compile();
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        compile.source(srcDir);
        compile.setIncludes(TEST_INCLUDES);
        compile.setExcludes(TEST_EXCLUDES);
        compile.setSourceCompatibility("1.5");
        compile.setTargetCompatibility("1.5");
        compile.setDestinationDir(destDir);
        scalaClasspath = context.mock(FileCollection.class);
        compile.setScalaClasspath(scalaClasspath);
        compile.setClasspath(context.mock(FileCollection.class));
    }

}
