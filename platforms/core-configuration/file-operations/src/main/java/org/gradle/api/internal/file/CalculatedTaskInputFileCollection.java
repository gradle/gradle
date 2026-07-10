/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CalculatedTaskInputFileCollection extends AbstractFileCollection implements LifecycleAwareValue {
    private final String taskPath;
    private MinimalFileSet calculatedFiles;
    private List<LifecycleAwareValue> targets;
    private Set<File> cachedFiles;
    private boolean taskIsExecuting;

    public CalculatedTaskInputFileCollection(TaskDependencyFactory taskDependencyFactory, String taskPath, MinimalFileSet calculatedFiles, Object[] inputs) {
        super(taskDependencyFactory);
        this.taskPath = taskPath;
        this.calculatedFiles = calculatedFiles;
        targets = new ArrayList<>(1 + inputs.length);
        for (Object input : inputs) {
            if (input instanceof LifecycleAwareValue) {
                targets.add((LifecycleAwareValue) input);
            }
        }
        if (calculatedFiles instanceof LifecycleAwareValue) {
            targets.add((LifecycleAwareValue) calculatedFiles);
        }
    }

    @Override
    public String getDisplayName() {
        return calculatedFiles.getDisplayName();
    }

    @Override
    public Set<File> getFiles() {
        if (!taskIsExecuting) {
            throw new IllegalStateException("Can only query " + calculatedFiles.getDisplayName() + " while task " + taskPath + " is running");
        }
        if (cachedFiles == null) {
            cachedFiles = calculatedFiles.getFiles();
        }
        return cachedFiles;
    }

    @Override
    public void prepareValue() {
        taskIsExecuting = true;
        for (LifecycleAwareValue target : targets) {
            target.prepareValue();
        }
    }

    @Override
    public void cleanupValue() {
        taskIsExecuting = false;
        cachedFiles = null;
        for (LifecycleAwareValue target : targets) {
            target.cleanupValue();
        }
        targets = null;
        // Discard the calculated files collection too, but need to retain the display name for it
    }
}
