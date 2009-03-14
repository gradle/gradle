/*
 * Copyright 2008 the original author or authors.
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
import org.apache.commons.io.FileUtils;
import org.gradle.api.DependencyManager;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ConfigurationResolveInstructionModifier;
import org.gradle.api.artifacts.ConfigurationResolver;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.JMockUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleState;
import org.gradle.external.javadoc.JavadocExecHandleBuilder;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import static java.util.Collections.EMPTY_LIST;
import java.util.List;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final List<File> srcDirs = WrapUtil.toList(new File("srcdir"));
    private final File destDir = new File("destdir");
    private final List<File> classpath = WrapUtil.toList(new File("classpath"));
    private JavadocExecHandleBuilder javadocExecHandleBuilderMock;
    private ExecHandle execHandleMock;
    private Javadoc task;
    private ExistingDirsFilter existingDirsFilter;
    private DependencyManager dependencyManager;
    private ConfigurationResolver configurationMock;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);

        javadocExecHandleBuilderMock = context.mock(JavadocExecHandleBuilder.class);
        execHandleMock = context.mock(ExecHandle.class);
        existingDirsFilter = context.mock(ExistingDirsFilter.class);
        dependencyManager = context.mock(DependencyManager.class);
        configurationMock = context.mock(ConfigurationResolver.class);

        task = new Javadoc(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        task.setExistentDirsFilter(existingDirsFilter);
        task.setDependencyManager(dependencyManager);
        task.setResolveInstruction(new ConfigurationResolveInstructionModifier("testConf"));
        JMockUtil.configureResolve(context, task.getResolveInstruction(), dependencyManager, configurationMock, classpath);
        task.setJavadocExecHandleBuilder(javadocExecHandleBuilderMock);
    }

    public AbstractTask getTask() {
        return task;
    }

    private void expectJavadocExecHandle() {
        context.checking(new Expectations(){{
            one(javadocExecHandleBuilderMock).execDirectory(getProject().getRootDir());
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).options(task.getOptions());
            will(returnValue(javadocExecHandleBuilderMock));
            one(javadocExecHandleBuilderMock).optionsFilename("javadoc.options");
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
        task.setSrcDirs(srcDirs);

        context.checking(new Expectations() {
            {
                one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
                will(returnValue(srcDirs));
            }
        });
        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.SUCCEEDED));
        }});

        task.execute();
    }

    @Test
    public void wrapsExecutionFailure() {
        final BuildException failure = new BuildException();

        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));
        }});
        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.FAILED));
            one(execHandleMock).getFailureCause();
            will(returnValue(failure));
        }});

        try {
            task.execute();
            fail();
        } catch (GradleException e) {
            assertThat(e.getCause().getMessage(), endsWith("Javadoc generation failed."));
            assertThat(e.getCause().getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void executionWithOptionalAtributes() {
        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);
        task.setMaxMemory("max-memory");
        task.setTitle("title");
        task.setVerbose(true);

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));
        }});
        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.SUCCEEDED));
        }});

        task.execute();
    }

    @Test
    public void executionWithIncludesAndExcludes() {
        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);
//        task.include("include");
        task.getOptions().exclude("exclude");

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));
        }});
        expectJavadocExecHandle();
        context.checking(new Expectations(){{
            one(execHandleMock).startAndWaitForFinish();
            will(returnValue(ExecHandleState.SUCCEEDED));
        }});

        task.execute();
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(destDir);
    }
}
