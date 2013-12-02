/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.internal.rules;

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.ide.visualstudio.internal.VisualStudioExtension;
import org.gradle.ide.visualstudio.internal.VisualStudioProject;
import org.gradle.ide.visualstudio.internal.VisualStudioSolution;
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask;
import org.gradle.model.ModelRule;

@SuppressWarnings("UnusedDeclaration")
public class CreateVisualStudioTasks extends ModelRule {

    public void createTasksForVisualStudio(VisualStudioExtension visualStudioExtension, TaskContainer tasks) {
        for (VisualStudioProject vsProject : visualStudioExtension.getProjectRegistry()) {
            vsProject.builtBy(createProjectsFileTask(tasks, vsProject));
            vsProject.builtBy(createFiltersFileTask(tasks, vsProject));
        }

        for (VisualStudioSolution vsSolution : visualStudioExtension.getSolutionRegistry()) {
            vsSolution.setLifecycleTask(tasks.create(vsSolution.getName() + "VisualStudio"));
            vsSolution.builtBy(createSolutionTask(tasks, vsSolution));

            // Lifecycle task for component
            tasks.create(vsSolution.getComponentName() + "VisualStudio").dependsOn(vsSolution);
        }
    }

    private Task createSolutionTask(TaskContainer tasks, VisualStudioSolution solution) {
        GenerateSolutionFileTask solutionFileTask = tasks.create(solution.getName() + "VisualStudioSolution", GenerateSolutionFileTask.class);
        solutionFileTask.setVisualStudioSolution(solution);
        return solutionFileTask;
    }

    private Task createProjectsFileTask(TaskContainer tasks, VisualStudioProject vsProject) {
        GenerateProjectFileTask task = tasks.create(vsProject.getName() + "VisualStudioProject", GenerateProjectFileTask.class);
        task.setVisualStudioProject(vsProject);
        return task;
    }

    private Task createFiltersFileTask(TaskContainer tasks, VisualStudioProject vsProject) {
        GenerateFiltersFileTask task = tasks.create(vsProject.getName() + "VisualStudioFilters", GenerateFiltersFileTask.class);
        task.setVisualStudioProject(vsProject);
        return task;
    }
}

