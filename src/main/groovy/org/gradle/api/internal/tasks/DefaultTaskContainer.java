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
package org.gradle.api.internal.tasks;

import org.gradle.api.*;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.ListenerBroadcast;
import groovy.lang.Closure;

public class DefaultTaskContainer extends DefaultDomainObjectContainer<Task> implements TaskContainer {
    private ListenerBroadcast<Action> addActions = new ListenerBroadcast<Action>(Action.class);

    @Override
    public void add(String name, Task object) {
        addActions.getSource().execute(object);
        super.add(name, object);
    }

    public Action<? super Task> whenTaskAdded(Action<? super Task> action) {
        addActions.add(action);
        return action;
    }

    public <T extends Task> Action<T> whenTaskAdded(final Class<T> type, final Action<T> action) {
        whenTaskAdded(new Action<Task>() {
            public void execute(Task task) {
                if (type.isInstance(task)) {
                    action.execute(type.cast(task));
                }
            }
        });
        return action;
    }

    public void whenTaskAdded(Closure closure) {
        addActions.add("execute", closure);
    }

    @Override
    public String getDisplayName() {
        return "task container";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found.", name));
    }
}
