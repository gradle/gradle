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

package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.util.ConfigureUtil;

/**
 * @author Hans Dockter
 */
public class DefaultTask extends AbstractTask {
    public DefaultTask() {
        this(null, null);
    }

    public DefaultTask(Project project, String name) {
        super(project, name);
    }

    public Task doFirst(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doFirst(convertClosureToAction(action));
        return this;
    }

    public Task doLast(Closure action) {
        if (action == null) {
            throw new InvalidUserDataException("Action must not be null!");
        }
        doLast(convertClosureToAction(action));
        return this;
    }

    public Task configure(Closure closure) {
        return (Task) ConfigureUtil.configure(closure, this);
    }

    private TaskAction convertClosureToAction(final Closure actionClosure) {
        actionClosure.setDelegate(getProject());
        actionClosure.setResolveStrategy(Closure.OWNER_FIRST);
        return new TaskAction() {
            public void execute(Task task) {
                actionClosure.call(new Object[] {task});
            }
        };
    }
}