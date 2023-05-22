/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.selection.BuildTaskSelector;

public class DefaultEntryTaskSelectorContext implements EntryTaskSelector.Context {
    final GradleInternal gradle;
    final BuildTaskSelector.BuildSpecificSelector taskSelector;

    public DefaultEntryTaskSelectorContext(GradleInternal gradle, BuildTaskSelector.BuildSpecificSelector taskSelector) {
        this.gradle = gradle;
        this.taskSelector = taskSelector;
    }

    @Override
    public TaskSelection getSelection(String taskPath) {
        return taskSelector.resolveTaskName(taskPath);
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }
}
