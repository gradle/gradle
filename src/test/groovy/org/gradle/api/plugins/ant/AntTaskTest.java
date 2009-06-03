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
package org.gradle.api.plugins.ant;

import org.apache.tools.ant.Target;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.BuildException;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.gradle.util.WrapUtil.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;

public class AntTaskTest {
    private Target target = new Target();
    private AntTask task = new AntTask(HelperUtil.createRootProject(), "task");

    @Before
    public void setUp() {
        target.setProject(new Project());
    }

    @Test
    public void executesTargetOnExecute() {
        TestTask testTask = new TestTask();
        testTask.setProject(target.getProject());
        target.addTask(testTask);

        task.setTarget(target);
        task.execute();

        assertTrue(testTask.executed);
    }

    @Test
    public void dependsOnTargetDependencies() {
        target.setDepends("a, b");

        task.setTarget(target);
        assertThat(task.getDependsOn(), equalTo(toSet((Object) "a", "b")));
    }

    @Test
    public void delegatesDescriptionToTarget() {
        target.setDescription("description");

        task.setTarget(target);
        assertThat(task.getDescription(), equalTo("description"));

        task.setDescription("new description");
        assertThat(target.getDescription(), equalTo("new description"));
    }

    public static class TestTask extends Task {
        boolean executed;

        @Override
        public void execute() throws BuildException {
            executed = true;
        }
    }
}
