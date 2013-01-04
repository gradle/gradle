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

package org.gradle.api.tasks.javadoc;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.external.javadoc.internal.JavadocExecHandleBuilder;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecException;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GFileUtils;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final TestFile testDir = tmpDir.getTestDirectory();
    private final File destDir = new File(testDir, "dest");
    private final File srcDir = new File(testDir, "srcdir");
    private final Set<File> classpath = WrapUtil.toSet(new File("classpath"));
    private JavadocExecHandleBuilder javadocExecHandleBuilderMock = context.mock(JavadocExecHandleBuilder.class);
    private ExecAction execActionMock = context.mock(ExecAction.class);
    private Javadoc task;
    private FileCollection configurationMock = new SimpleFileCollection(classpath);
    private String executable = "somepath";

    @Before
    public void setUp() {
        task = createTask(Javadoc.class);
        task.setClasspath(configurationMock);
        task.setExecutable(executable);
        task.setJavadocExecHandleBuilder(javadocExecHandleBuilderMock);
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
            one(javadocExecHandleBuilderMock).optionsFile(new File(getProject().getBuildDir(), "tmp/taskname/javadoc.options"));
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).getExecHandle();
            will(returnValue(execActionMock));
            one(javadocExecHandleBuilderMock).setExecutable(executable);
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
            one(execActionMock).execute();
        }});
    }

    @Test
    public void wrapsExecutionFailure() {
        final ExecException failure = new ExecException(null);

        task.setDestinationDir(destDir);
        task.source(srcDir);

        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execActionMock).execute();
            will(throwException(failure));
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
