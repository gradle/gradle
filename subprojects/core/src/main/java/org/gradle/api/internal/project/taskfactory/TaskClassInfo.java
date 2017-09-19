/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableList;

public class TaskClassInfo {
    private final boolean incremental;
    private final ImmutableList<TaskActionFactory> taskActionFactories;
    private final TaskClassValidator validator;

    public TaskClassInfo(boolean incremental, ImmutableList<TaskActionFactory> taskActionFactories, TaskClassValidator validator) {
        this.incremental = incremental;
        this.taskActionFactories = taskActionFactories;
        this.validator = validator;
    }

    public TaskClassValidator getValidator() {
        return validator;
    }

    public ImmutableList<TaskActionFactory> getTaskActionFactories() {
        return taskActionFactories;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isCacheable() {
        return validator.isCacheable();
    }
}
