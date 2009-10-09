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
package org.gradle.api.tasks.javadoc;

import org.apache.tools.ant.BuildException;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.external.javadoc.JavadocExecHandleBuilder;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.integtests.TestFile;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleState;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final TestFile testDir = tmpDir.getDir();
    private final File destDir = new File(testDir, "dest");
    private final File srcDir = new File(testDir, "srcdir");
    private final Set<File> classpath = WrapUtil.toSet(new File("classpath"));
    private final File optionsFile = new File(destDir, "javadoc.options");
    private JavadocExecHandleBuilder javadocExecHandleBuilderMock = context.mock(JavadocExecHandleBuilder.class);
    private ExecHandle execHandleMock = context.mock(ExecHandle.class);
    private Javadoc task;
    private FileCollection configurationMock = context.mock(FileCollection.class);

    @Before
    public void setUp() {
        super.setUp();
        task = createTask(Javadoc.class);
        task.setClasspath(configurationMock);
        task.setJavadocExecHandleBuilder(javadocExecHandleBuilderMock);
        task.setOptionsFile(optionsFile);
        context.checking(new Expectations() {{
            allowing(configurationMock).getFiles(); will(returnValue(classpath));
        }});

        GFileUtils.touch(new File(srcDir, "file.java"));
    }

    public ConventionTask getTask() {
        return task;
    }

    private void expectJavadocExecHandle() {
        context.checking(new Expectations(){{
            one(javadocExecHandleBuilderMock).execDirectory(getProject().getRootDir());
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).options(task.getOptions());
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).optionsFile(optionsFile);
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).destinationDirectory(destDir);
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).getExecHandle();
            will(returnValue(execHandleMock));
        }});
    }

    @Test
    public void defaultExecution() {
        task.setDestinationDir(destDir);
        task.source(srcDir);

        expectJavadocExecHandle();
        expectJavadocExec();

        task.execute();
    }

    private void expectJavadocExec() {
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.SUCCEEDED));
        }});
    }

    @Test
    public void wrapsExecutionFailure() {
        final BuildException failure = new BuildException();

        task.setDestinationDir(destDir);
        task.source(srcDir);

        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.FAILED));
            one(execHandleMock).getFailureCause();
            will(returnValue(failure));
        }});

        try {
            task.generate();
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), endsWith("Javadoc generation failed."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void executionWithOptionalAtributes() {
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setMaxMemory("max-memory");
        task.setVerbose(true);

        expectJavadocExecHandle();
        expectJavadocExec();

        task.execute();
    }

    @Test
    public void setsTheWindowAndDocTitleIfNotSet() {
        task.setDestinationDir(destDir);
        task.source(srcDir);
        task.setTitle("title");

        expectJavadocExecHandle();
        expectJavadocExec();

        task.execute();
        StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
        assertThat(options.getDocTitle(), equalTo("title"));
        assertThat(options.getWindowTitle(), equalTo("title"));
    }
}
