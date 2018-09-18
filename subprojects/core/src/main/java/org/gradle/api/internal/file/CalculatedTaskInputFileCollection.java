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
import org.gradle.api.internal.tasks.LifecycleAwareTaskProperty;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CalculatedTaskInputFileCollection extends AbstractFileCollection implements LifecycleAwareTaskProperty {
    private final String taskPath;
    private MinimalFileSet calculatedFiles;
    private List<LifecycleAwareTaskProperty> targets;
    private Set<File> cachedFiles;
    private boolean taskIsExecuting;

    public CalculatedTaskInputFileCollection(String taskPath, MinimalFileSet calculatedFiles, Object[] inputs) {
        this.taskPath = taskPath;
        this.calculatedFiles = calculatedFiles;
        targets = new ArrayList<LifecycleAwareTaskProperty>(1 + inputs.length);
        for (Object input : inputs) {
            if (input instanceof LifecycleAwareTaskProperty) {
                targets.add((LifecycleAwareTaskProperty) input);
            }
        }
        if (calculatedFiles instanceof LifecycleAwareTaskProperty) {
            targets.add((LifecycleAwareTaskProperty) calculatedFiles);
        }
    }

    @Override
    public String getDisplayName() {
        return calculatedFiles.getDisplayName();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencyInternal.EMPTY;
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
        for (LifecycleAwareTaskProperty target : targets) {
            target.prepareValue();
        }
    }

    @Override
    public void cleanupValue() {
        taskIsExecuting = false;
        cachedFiles = null;
        for (LifecycleAwareTaskProperty target : targets) {
            target.cleanupValue();
        }
        targets = null;
        // Discard the calculated files collection too, but need to retain the display name for it
    }
}
