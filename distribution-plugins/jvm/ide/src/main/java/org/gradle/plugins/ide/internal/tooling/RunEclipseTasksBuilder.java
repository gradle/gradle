/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.ArrayList;
import java.util.List;

public class RunEclipseTasksBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return isSyncModel(modelName) || isAutoBuildModel(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        StartParameter startParameter = project.getGradle().getStartParameter();
        List<String> taskPaths = new ArrayList<String>();
        taskPaths.addAll(startParameter.getTaskNames());

        boolean isSyncModel = isSyncModel(modelName);
        boolean isAutoBuildModel = isAutoBuildModel(modelName);

        for (Project p : project.getAllprojects()) {
            EclipseModel model = p.getExtensions().findByType(EclipseModel.class);
            if (model != null) {
                if (isSyncModel) {
                    for (Task t : model.getSynchronizationTasks().getDependencies(null)) {
                        taskPaths.add(t.getPath());
                    }
                }
                if (isAutoBuildModel) {
                    for (Task t : model.getAutoBuildTasks().getDependencies(null)) {
                        taskPaths.add(t.getPath());
                    }
                }
            }
        }

        if (taskPaths.isEmpty()) {
            // If no tasks is specified then the default tasks will be executed.
            // To work around this, we assign a new empty task for execution.
            String placeHolderTaskName = placeHolderTaskName(project, "nothing");
            project.task(placeHolderTaskName);
            taskPaths.add(placeHolderTaskName);
        }

        project.getGradle().getStartParameter().setTaskNames(taskPaths);
        return null;
    }

    private static boolean isSyncModel(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.RunEclipseSynchronizationTasks");
    }


    private static boolean isAutoBuildModel(String modelName) {
        return modelName.equals("org.gradle.tooling.model.eclipse.RunEclipseAutoBuildTasks");
    }

    private static String placeHolderTaskName(Project project, String baseName) {
        if (project.getTasks().findByName(baseName) == null) {
            return baseName;
        } else {
            return placeHolderTaskName(project, baseName + "_");
        }
    }
}
