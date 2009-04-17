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

import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.tasks.TaskContainer;

public class DefaultTaskContainer extends DefaultDomainObjectContainer<Task> implements TaskContainer {
    @Override
    public void add(String name, Task object) {
        super.add(name, object);
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
