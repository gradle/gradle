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

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class GroovyCompileTest extends AbstractCompileTest {
    static final List TEST_GROOVY_CLASSPATH = WrapUtil.toList("groovy.jar");

    private GroovyCompile testObj;

    AntJavac antJavacCompileMock;
    AntGroovyc antGroovycCompileMock;

    JUnit4Mockery context = new JUnit4Mockery();

    public Compile getCompile() {
        return testObj;
    }

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        getProject().setProjectDir(AbstractCompileTest.TEST_ROOT_DIR);
        testObj = new GroovyCompile(getProject(), AbstractTaskTest.TEST_TASK_NAME, getTasksGraph());
        antJavacCompileMock = context.mock(AntJavac.class);
        antGroovycCompileMock = context.mock(AntGroovyc.class);
        testObj.setAntGroovyCompile(antGroovycCompileMock);
        testObj.antCompile = antJavacCompileMock;
    }

    public AbstractTask getTask() {
        return testObj;
    }

    @Test
    public void testExecute() {
        setUpMocksAndAttributes(testObj);
        context.checking(new Expectations() {
            {
                one(antJavacCompileMock).execute(testObj.getSrcDirs(), testObj.getIncludes(), testObj.getExcludes(), testObj.getDestinationDir(),
                        GUtil.addLists(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH, AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH),
                        testObj.getSourceCompatibility(), testObj.getTargetCompatibility(), testObj.getOptions(), testObj.getProject().getAnt());
                one(antGroovycCompileMock).execute(testObj.getProject().getAnt(), testObj.getGroovySourceDirs(), testObj.getGroovyIncludes(), testObj.getGroovyExcludes(),
                        testObj.getGroovyJavaIncludes(), testObj.getGroovyJavaExcludes(), testObj.getDestinationDir(),
                        GUtil.addLists(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH, AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH),
                        testObj.getSourceCompatibility(), testObj.getTargetCompatibility(), testObj.getOptions(),
                        TEST_GROOVY_CLASSPATH);
            }
        });
        testObj.execute();
    }

    void setUpMocksAndAttributes(GroovyCompile compile) {
        super.setUpMocksAndAttributes((Compile) compile);
        compile.setGroovyClasspath(TEST_GROOVY_CLASSPATH);
        compile.setGroovySourceDirs(WrapUtil.toList(new File("groovySourceDir1"), new File("groovySourceDir2")));
        compile.existentDirsFilter = getGroovyCompileExistingDirsFilterMock();
    }

    @Test
    public void testGroovyIncludes() {
        checkIncludesExcludes("groovyInclude");
    }

    @Test
    public void testGroovyExcludes() {
        checkIncludesExcludes("groovyExclude");
    }
}
