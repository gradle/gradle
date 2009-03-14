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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import static java.util.Collections.EMPTY_LIST;
import java.util.List;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final List<File> srcDirs = WrapUtil.toList(new File("srcdir"));
    private final File destDir = new File("destdir");
    private final List<File> classpath = WrapUtil.toList(new File("classpath"));
    private AntJavadoc antJavadoc;
    private Javadoc task;
    private ExistingDirsFilter existingDirsFilter;
    private DependencyManager dependencyManager;
    private ConfigurationResolver configurationMock;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);

        antJavadoc = context.mock(AntJavadoc.class);
        existingDirsFilter = context.mock(ExistingDirsFilter.class);
        dependencyManager = context.mock(DependencyManager.class);
        configurationMock = context.mock(ConfigurationResolver.class);

        task = new Javadoc(getProject(), AbstractTaskTest.TEST_TASK_NAME);
//        task.setAntJavadoc(antJavadoc);
        task.setExistentDirsFilter(existingDirsFilter);
        task.setDependencyManager(dependencyManager);
        task.setResolveInstruction(new ConfigurationResolveInstructionModifier("testConf"));
        JMockUtil.configureResolve(context, task.getResolveInstruction(), dependencyManager, configurationMock, classpath);
    }

    public AbstractTask getTask() {
        return task;
    }

    @Test
    public void defaultExecution() {
        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);

        context.checking(new Expectations() {
            {
                one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
                will(returnValue(srcDirs));
                one(antJavadoc).execute(srcDirs, destDir, classpath, null, null, EMPTY_LIST, EMPTY_LIST, false, getProject().getAnt());
            }
        });

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

            one(antJavadoc).execute(srcDirs, destDir, classpath, null, null, EMPTY_LIST, EMPTY_LIST, false, getProject().getAnt());
            will(throwException(failure));
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

            one(antJavadoc).execute(srcDirs, destDir, classpath, "title", "max-memory", EMPTY_LIST, EMPTY_LIST, true, getProject().getAnt());
        }});

        task.execute();
    }

    @Test
    public void executionWithIncludesAndExcludes() {
        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);
//        task.include("include");
//        task.exclude("exclude");

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));

            one(antJavadoc).execute(srcDirs, destDir, classpath, null, null, WrapUtil.toList("include"), WrapUtil.toList("exclude"), false, getProject().getAnt());
        }});

        task.execute();
    }
}
