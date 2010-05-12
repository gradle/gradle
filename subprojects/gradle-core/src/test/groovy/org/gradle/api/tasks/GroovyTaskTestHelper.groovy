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
import org.gradle.api.internal.AbstractTask
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class GroovyTaskTestHelper {
    public static void checkAddActionsWithClosures(AbstractTask task) {
        task.deleteAllActions();
        boolean action1Called = false
        Closure action1 = {action1Called = true}
        boolean action2Called = false
        Closure action2 = {Task t -> action2Called = true}
        task.doFirst(action1)
        task.doLast(action2)
        assertEquals([action1, action2], task.actions.collect { it.closure })
    }

    public static void checkConfigure(AbstractTask task) {
        Closure action1 = { Task t -> }
        assertSame(task, task.configure {
            doFirst(action1)
        });
        assertEquals(1, task.actions.size())
    }
}
