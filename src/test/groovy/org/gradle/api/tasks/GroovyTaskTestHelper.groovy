/*
 * Copyright 2007-2008 the original author or authors.
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
 
package org.gradle.api.tasks

import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.gradle.execution.Dag
import static org.junit.Assert.*
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.AbstractTask;

/**
 * @author Hans Dockter
 */
class GroovyTaskTestHelper {
    public static void checkAddActionsWithClosures(AbstractTask task) {
        task.setActions(new ArrayList());
        boolean action1Called = false
        Closure action1 = {Task t -> action1Called = true}
        boolean action2Called = false
        Closure action2 = {Task t -> action2Called = true}
        boolean action3Called = false
        Closure action3 = {Task t, Dag tasksGraph -> action3Called = true}
        task.doFirst(action1)
        task.doLast(action2)
        task.doLast(action3)
        task.execute()
        assertTrue(action1Called)
        assertTrue(action2Called)
        assertTrue(action3Called)
    }

    public static void checkConfigure(AbstractTask task) {
        Closure action1 = { Task t, Dag dag -> }
        assertSame(task, task.configure {
            doFirst(action1)
        });
        assertEquals(1, task.actions.size())
    }
}
