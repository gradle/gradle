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
package org.gradle.api.tasks.testing;

import org.gradle.api.Project;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractTestFrameworkInstanceTest {

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    protected Project projectMock;
    protected AntTest testMock;

    protected final File projectDir = new File("projectDir");
    protected final File testClassesDir = new File("testClassesDir");
    protected final List<File> testSrcDirs = Arrays.asList(new File("testSrcDirs"));
    protected final File testResultsDir = new File("testResultsDir");
    protected final File testReportDir = new File("testReportDir");

    protected void setUp() throws Exception {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        projectMock = context.mock(Project.class);
        testMock = context.mock(AntTest.class);
    }

    protected void expectHandleEmptyIncludesExcludes() {
        context.checking(new Expectations() {{
            one(testMock).getIncludes();
            will(returnValue(null));
            one(testMock).include("**/*Tests.class", "**/*Test.class");
            one(testMock).getExcludes();
            will(returnValue(null));
            one(testMock).exclude("**/Abstract*.class");
        }});
    }
}
