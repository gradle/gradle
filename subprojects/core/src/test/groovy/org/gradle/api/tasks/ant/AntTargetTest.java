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
import org.gradle.api.Task;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.HelperUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Set;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AntTargetTest {
    private final Target antTarget = new Target();
    private final DefaultProject project = HelperUtil.createRootProject();
    private final AntTarget task = HelperUtil.createTask(AntTarget.class, project);
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    private final File baseDir = testDir.getTestDirectory();

    @Before
    public void setUp() {
        antTarget.setProject(new Project());
    }

    @Test
    public void executesTargetOnExecute() {
        TestTask testTask = new TestTask();
        testTask.setProject(antTarget.getProject());
        antTarget.addTask(testTask);

        task.setTarget(antTarget);
        task.setBaseDir(baseDir);
        task.executeAntTarget();

        assertTrue(testTask.executed);
    }

    @Test
    public void dependsOnTargetDependencies() {
        Task a = project.getTasks().add("a");
        Task b = project.getTasks().add("b");
        antTarget.setDepends("a, b");

        task.setTarget(antTarget);
        Set dependencies = task.getTaskDependencies().getDependencies(task);
        assertThat(dependencies, equalTo((Set) toSet(a, b)));
    }

    @Test
    public void delegatesDescriptionToTarget() {
        antTarget.setDescription("description");

        task.setTarget(antTarget);
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
