/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.buildinit.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.custominit.CustomInit;

import java.util.Optional;

public abstract class CustomInitPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        System.out.println("Applying custom-init plugin to " + project);

        if (project.getParent() == null) {
            System.out.println("Applying custom-init plugin");
            System.out.println("Registering custom-init task");

            TaskProvider<CustomInit> taskProvider = project.getTasks().register("custom-init", CustomInit.class);
            String defaultTask = Optional.ofNullable(((ProjectInternal) project).getGradle().getStartParameter().getCustomInitPluginTask()).orElse("help");
            taskProvider.configure(init -> {
                String dependency = ":" + defaultTask;
                System.out.println("Adding dependency to " + dependency + " from " + init);
                //init.dependsOn(dependency);
            });
            project.defaultTasks(defaultTask);
        } else {
            System.out.println("Skipping as project has parent: " + project.getParent());
        }
    }
}
