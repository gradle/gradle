/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.GradleScriptException;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.util.ExistingDirsFilter;
import org.gradle.util.HelperUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.gradle.test.util.Check;
import org.gradle.test.util.Check.Execute;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class CleanTest extends AbstractConventionTaskTest {
    private Clean clean;

    private JUnit4Mockery context = new JUnit4Mockery();

    private ExistingDirsFilter existentDirsFilterMock;

    @Before
    public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE);
        clean = new Clean(getProject(), AbstractTaskTest.TEST_TASK_NAME);
        existentDirsFilterMock = context.mock(ExistingDirsFilter.class);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    public AbstractTask getTask() {
        return clean;
    }

    @Test
    public void testClean() {
        assertNull(clean.getDir());
    }

    @Test
    public void testExecute() throws IOException {
        clean.setDir(HelperUtil.makeNewTestDir());
        context.checking(new Expectations() {
            {
                allowing(existentDirsFilterMock).checkExistenceAndThrowStopActionIfNot(clean.getDir());
            }
        });
        (new File(clean.getDir(), "somefile")).createNewFile();
        clean.execute();
        assertFalse(clean.getDir().exists());
    }

    @Test(expected = GradleScriptException.class)
    public void testExecuteWithNullDir() {
        clean.execute();
    }

}
