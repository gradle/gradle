/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.GradleScriptException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import static org.gradle.util.WrapUtil.*;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class GroovyCompileTest extends AbstractCompileTest {
    static final List TEST_GROOVY_CLASSPATH = toList(new File("groovy.jar"));

    private GroovyCompile testObj;

    AntGroovyc antGroovycCompileMock;

    JUnit4Mockery context = new JUnit4Mockery();

    public Compile getCompile() {
        return testObj;
    }

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        testObj = createTask(GroovyCompile.class);
        antGroovycCompileMock = context.mock(AntGroovyc.class);
        testObj.setAntGroovyCompile(antGroovycCompileMock);
        testObj.antCompile = context.mock(AntJavac.class);
    }

    public ConventionTask getTask() {
        return testObj;
    }

    public void testExecute(final int numFilesCompiled) {
        setUpMocksAndAttributes(testObj, TEST_GROOVY_CLASSPATH);
        context.checking(new Expectations() {
            {
                one(antGroovycCompileMock).execute(testObj.getProject().getAnt(), testObj.getSrcDirs(),
                        testObj.getIncludes(), testObj.getExcludes(),
                        testObj.getDestinationDir(), TEST_DEPENDENCY_MANAGER_CLASSPATH,
                        testObj.getSourceCompatibility(), testObj.getTargetCompatibility(), testObj.getGroovyOptions(),
                        testObj.getOptions(), TEST_GROOVY_CLASSPATH);
                one(antGroovycCompileMock).getNumFilesCompiled();  will(returnValue(numFilesCompiled));
            }
        });
        testObj.execute();
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
        setUpMocksAndAttributes(testObj, Collections.emptyList());
        try {
            testObj.execute();
        } catch (GradleScriptException e) {
            assertThat(e.getCause(), Matchers.is(InvalidUserDataException.class));
            return;
        }
        Assert.fail();
    }

    void setUpMocksAndAttributes(GroovyCompile compile, final List groovyClasspath) {
        super.setUpMocksAndAttributes(compile);

        final FileCollection groovyClasspathCollection = context.mock(FileCollection.class);
        context.checking(new Expectations(){{
            allowing(groovyClasspathCollection).getFiles();
            will(returnValue(new LinkedHashSet<File>(groovyClasspath)));
        }});

        compile.setGroovyClasspath(groovyClasspathCollection);
        compile.setSrcDirs(toList(new File("groovySourceDir1"), new File("groovySourceDir2")));
        compile.existentDirsFilter = getGroovyCompileExistingDirsFilterMock(compile);
    }
}
