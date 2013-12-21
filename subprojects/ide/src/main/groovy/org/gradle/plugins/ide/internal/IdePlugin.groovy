/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal;


import org.apache.commons.lang.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Delete

public abstract class IdePlugin implements Plugin<Project> {
    private Task lifecycleTask;
    private Task cleanTask;
    protected Project project;

    public void apply(Project target) {
        project = target;
        String lifecyleTaskName = getLifecycleTaskName();
        lifecycleTask = target.task(lifecyleTaskName);
        lifecycleTask.setGroup("IDE");
        cleanTask = target.task(cleanName(lifecyleTaskName));
        cleanTask.setGroup("IDE");
        onApply(target);
    }

    public Task getLifecycleTask() {
        return lifecycleTask;
    }

    public Task getCleanTask() {
        return cleanTask;
    }

    public Task getCleanTask(Task worker) {
        return project.getTasks().getByName(cleanName(worker.getName()));
    }

    protected String cleanName(String taskName) {
        return String.format("clean%s", StringUtils.capitalize(taskName));
    }

    protected void addWorker(Task worker, boolean includeInClean = true) {
        lifecycleTask.dependsOn(worker)
        Delete cleanWorker = project.tasks.create(cleanName(worker.name), Delete.class)
        cleanWorker.delete(worker.getOutputs().getFiles())
        if (includeInClean) {
            cleanTask.dependsOn(cleanWorker)
        }
    }
    
    protected void onApply(Project target) {
    }

    protected abstract String getLifecycleTaskName();
}
