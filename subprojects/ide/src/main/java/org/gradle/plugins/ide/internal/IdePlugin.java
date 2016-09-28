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

import org.apache.commons.lang.StringUtils;
import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.Delete;

public abstract class IdePlugin implements Plugin<Project> {

    private Task lifecycleTask;
    private Task cleanTask;
    protected Project project;

    @Override
    public void apply(Project target) {
        project = target;
        String lifecycleTaskName = getLifecycleTaskName();
        lifecycleTask = target.task(lifecycleTaskName);
        lifecycleTask.setGroup("IDE");
        cleanTask = target.task(cleanName(lifecycleTaskName));
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

    public void addWorker(Task worker) {
        addWorker(worker, true);
    }

    public void addWorker(Task worker, boolean includeInClean) {
        lifecycleTask.dependsOn(worker);
        Delete cleanWorker = project.getTasks().create(cleanName(worker.getName()), Delete.class);
        cleanWorker.delete(worker.getOutputs().getFiles());
        if (includeInClean) {
            cleanTask.dependsOn(cleanWorker);
        }
    }

    protected void onApply(Project target) {
    }

    protected abstract String getLifecycleTaskName();

    /**
     * Executes the provided Action after all projects have been evaluated.
     * Action will only be added once per provided key. Any subsequent calls for the same key will be ignored.
     * This permits the plugin to be applied in multiple subprojects, with the postprocess action executed once only.
     */
    protected void postProcess(String key, final Action<? super Gradle> action) {
        Project rootProject = project.getRootProject();
        ExtraPropertiesExtension rootExtraProperties = rootProject.getExtensions().getByType(ExtraPropertiesExtension.class);
        String extraPropertyName = "org.gradle." + key + ".postprocess.applied";
        if (!rootExtraProperties.has(extraPropertyName)) {
            project.getGradle().addBuildListener(new BuildAdapter() {
                @Override
                public void projectsEvaluated(Gradle gradle) {
                    action.execute(gradle);
                }
            });
            rootExtraProperties.set(extraPropertyName, true);
        }
    }
}
