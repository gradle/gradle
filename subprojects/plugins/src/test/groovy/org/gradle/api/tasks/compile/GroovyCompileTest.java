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

package org.gradle.api.tasks.compile;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.GFileUtils;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.core.IsNull;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.gradle.util.WrapUtil.toList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class GroovyCompileTest extends AbstractCompileTest {
    static final List<File> TEST_GROOVY_CLASSPATH = toList(new File("groovy.jar"));

    private GroovyCompile testObj;

    Compiler<GroovyJavaJointCompileSpec> groovyCompilerMock;

    JUnit4Mockery context = new JUnit4GroovyMockery();

    public AbstractCompile getCompile() {
        return testObj;
    }

    @Before
    public void setUp() {
        testObj = createTask(GroovyCompile.class);
        groovyCompilerMock = context.mock(Compiler.class);
        testObj.setCompiler(groovyCompilerMock);

        GFileUtils.touch(new File(srcDir, "incl/file.groovy"));
    }

    public ConventionTask getTask() {
        return testObj;
    }

    public void testExecute(final int numFilesCompiled) {
        setUpMocksAndAttributes(testObj, TEST_GROOVY_CLASSPATH);
        context.checking(new Expectations(){{
            WorkResult result = context.mock(WorkResult.class);

            one(groovyCompilerMock).execute(with(IsNull.<GroovyJavaJointCompileSpec>notNullValue()));
            will(returnValue(result));
            allowing(result).getDidWork();
            will(returnValue(numFilesCompiled > 0));
        }});

        testObj.compile();
    }

    @Test
    public void testExecuteDoingWork() {
        testExecute(7);
        assertTrue(testObj.getDidWork());
    }

    @Test
    public void testExecuteNotDoingWork() {
        testExecute(0);
        assertFalse(testObj.getDidWork());
    }

    @Test
    public void testExecuteWithEmptyGroovyClasspath() {
        setUpMocksAndAttributes(testObj, Collections.<File>emptyList());
        try {
            testObj.compile();
        } catch (InvalidUserDataException e) {
            return;
        }
        Assert.fail();
    }

    void setUpMocksAndAttributes(GroovyCompile compile, final List<File> groovyClasspath) {
        super.setUpMocksAndAttributes(compile);

        final FileCollection groovyClasspathCollection = context.mock(FileCollection.class);
        context.checking(new Expectations(){{
            allowing(groovyClasspathCollection).getFiles();
            will(returnValue(new LinkedHashSet<File>(groovyClasspath)));
        }});

        compile.setGroovyClasspath(groovyClasspathCollection);
        compile.source(srcDir);
    }
}
