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

package org.gradle.api.internal.tasks.cache.diagnostics;

import org.gradle.api.Task;

public class TaskUpToDate extends TaskExecutionEvent {
    public TaskUpToDate(Task task) {
        super(task);
    }

    @Override
    public boolean isCached() {
        return false;
    }

    @Override
    public boolean isTaskCacheable() {
        return false;
    }

    @Override
    public boolean isUpToDate() {
        return true;
    }
}
