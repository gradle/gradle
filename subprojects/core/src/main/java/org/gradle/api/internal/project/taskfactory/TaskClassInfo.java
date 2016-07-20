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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.internal.Factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TaskClassInfo {
    private TaskClassValidator validator;
    private final List<Factory<Action<Task>>> taskActions = new ArrayList<Factory<Action<Task>>>();
    private boolean incremental;
    private boolean cacheable;

    public TaskClassValidator getValidator() {
        return validator;
    }

    public void setValidator(TaskClassValidator validator) {
        this.validator = validator;
    }

    public List<Factory<Action<Task>>> getTaskActions() {
        return taskActions;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    public Set<String> getNonAnnotatedPropertyNames() {
        return validator.getNonAnnotatedPropertyNames();
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public void setCacheable(boolean cacheable) {
        this.cacheable = cacheable;
    }
}
