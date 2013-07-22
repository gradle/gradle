/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing;

import org.gradle.api.AntBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@RunWith(JMock.class)
public abstract class AbstractTestFrameworkTest {

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    protected Project projectMock;
    protected Test testMock;

    protected final File projectDir = new File("projectDir");
    protected final File testClassesDir = new File("testClassesDir");
    protected final List<File> testSrcDirs = Arrays.asList(new File("testSrcDirs"));
    protected final File testResultsDir = new File("testResultsDir");
    protected final File testReportDir = new File("testReportDir");
    protected final File temporaryDir = new File("tempDir");
    protected AntBuilder antBuilderMock;
    protected FileCollection classpathMock;
    protected FileTree classpathAsFileTreeMock;

    protected void setUp() throws Exception {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        projectMock = context.mock(Project.class);
        testMock = context.mock(Test.class);
        antBuilderMock = context.mock(AntBuilder.class);
        classpathMock = context.mock(FileCollection.class);
        classpathAsFileTreeMock = context.mock(FileTree.class);

        context.checking(new Expectations(){{
            allowing(testMock).getProject();
            will(returnValue(projectMock));
        }});
    }
}
