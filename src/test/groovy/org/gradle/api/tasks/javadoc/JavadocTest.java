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

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractConventionTaskTest;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import static java.util.Collections.*;
import java.util.List;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class JavadocTest extends AbstractConventionTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private AntJavadoc antJavadoc;
    private Javadoc task;
    private ExistingDirsFilter existingDirsFilter;

    @Before
    public void setUp() {
        super.setUp();

        context.setImposteriser(ClassImposteriser.INSTANCE);

        antJavadoc = context.mock(AntJavadoc.class);
        existingDirsFilter = context.mock(ExistingDirsFilter.class);

        task = new Javadoc(getProject(), AbstractTaskTest.TEST_TASK_NAME, getTasksGraph());
        task.setAntJavadoc(antJavadoc);
        task.setExistentDirsFilter(existingDirsFilter);
    }

    public AbstractTask getTask() {
        return task;
    }

    @Test public void defaultExecution() {
        final List<File> srcDirs = WrapUtil.toList(new File("srcdir"));
        final File destDir = new File("destdir");

        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));

            one(antJavadoc).execute(srcDirs, destDir, null, null, EMPTY_LIST, EMPTY_LIST, getProject().getAnt());
        }});

        task.execute();
    }

    @Test public void executionWithOptionalAtributes() {
        final List<File> srcDirs = WrapUtil.toList(new File("srcdir"));
        final File destDir = new File("destdir");

        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);
        task.setMaxMemory("max-memory");
        task.setTitle("title");

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));

            one(antJavadoc).execute(srcDirs, destDir, "title", "max-memory", EMPTY_LIST, EMPTY_LIST, getProject().getAnt());
        }});

        task.execute();
    }

    @Test public void executionWithIncludesAndExcludes() {
        final List<File> srcDirs = WrapUtil.toList(new File("srcdir"));
        final File destDir = new File("destdir");

        task.setDestinationDir(destDir);
        task.setSrcDirs(srcDirs);
        task.include("include");
        task.exclude("exclude");

        context.checking(new Expectations() {{
            one(existingDirsFilter).checkDestDirAndFindExistingDirsAndThrowStopActionIfNone(destDir, srcDirs);
            will(returnValue(srcDirs));

            one(antJavadoc).execute(srcDirs, destDir, null, null, WrapUtil.toList("include"), WrapUtil.toList("exclude"), getProject().getAnt());
        }});

        task.execute();
    }
}
