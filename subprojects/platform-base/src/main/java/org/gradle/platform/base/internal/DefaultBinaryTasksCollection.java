/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;

public class DefaultBinaryTasksCollection extends DefaultDomainObjectSet<Task> implements BinaryTasksCollection {
    private BinarySpec binary;

    public DefaultBinaryTasksCollection(BinarySpec binarySpecInternal) {
        super(Task.class);
        this.binary = binarySpecInternal;
    }

    public Task getBuild() {
        return binary.getBuildTask();
    }

    protected <T extends Task> T findSingleTaskWithType(Class<T> type) {
        DomainObjectSet<T> tasks = withType(type);
        if (tasks.size() == 0) {
            return null;
        }
        if (tasks.size() > 1) {
            throw new UnknownDomainObjectException(String.format("Multiple tasks with type '%s' found.", type.getSimpleName()));
        }
        return tasks.iterator().next();
    }
}
