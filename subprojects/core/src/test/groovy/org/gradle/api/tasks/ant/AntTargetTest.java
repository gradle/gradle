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
package org.gradle.api.tasks.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

public class AntTargetTest {
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass());

    private final Target antTarget = new Target();
    private final File baseDir = testDir.getTestDirectory();
    private final ProjectInternal project = TestUtil.create(testDir).rootProject();
    private final AntTarget task = TestUtil.createTask(AntTarget.class, project);

    @Before
    public void setUp() {
        antTarget.setProject(new Project());
    }

    @Test
    public void executesTargetOnExecute() {
        TestTask testTask = new TestTask();
        testTask.setProject(antTarget.getProject());
        antTarget.addTask(testTask);
        task.getTarget().set(antTarget);
        task.getBaseDir().set(baseDir);
        task.executeAntTarget();
        assertTrue(testTask.executed);
    }

    @Test
    public void delegatesDescriptionToTarget() {
        antTarget.setDescription("description");

        task.getTarget().set(antTarget);
        assertThat(task.getDescription(), equalTo("description"));

        antTarget.setDescription("new description");
        assertThat(task.getDescription(), equalTo("new description"));
    }

    public class TestTask extends org.apache.tools.ant.Task {
        boolean executed;

        @Override
        public void execute() throws BuildException {
            assertThat(antTarget.getProject().getBaseDir(), equalTo(baseDir));
            executed = true;
        }
    }
}
