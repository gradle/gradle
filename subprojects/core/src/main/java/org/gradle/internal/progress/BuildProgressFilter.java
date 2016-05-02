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
package org.gradle.internal.progress;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;

//Filters out nested projects
public class BuildProgressFilter implements BuildListener, TaskExecutionGraphListener, TaskExecutionListener, ProjectEvaluationListener {

    private Gradle gradle;
    private BuildProgressLogger logger;

    public BuildProgressFilter(BuildProgressLogger logger) {
        this.logger = logger;
    }

    public void buildStarted(Gradle gradle) {
        if (gradle.getParent() == null) {
            this.gradle = gradle;
            logger.buildStarted();
        }
    }

    public void settingsEvaluated(Settings settings) {
        if (settings.getGradle() == gradle) {
            logger.settingsEvaluated();
        }
    }

    public void projectsLoaded(Gradle gradle) {
        if (gradle == this.gradle) {
            logger.projectsLoaded(gradle.getRootProject().getAllprojects().size());
        }
    }

    public void graphPopulated(TaskExecutionGraph graph) {
        if (gradle != null && graph == gradle.getTaskGraph()) {
            logger.graphPopulated(graph.getAllTasks().size());
        }
    }

    public void beforeEvaluate(Project project) {
        if (project.getGradle() == gradle) {
            logger.beforeEvaluate(project.getPath());
        }
    }

    public void afterEvaluate(Project project, ProjectState state) {
        if (project.getGradle() == gradle) {
            logger.afterEvaluate(project.getPath());
        }
    }

    public void projectsEvaluated(Gradle gradle) {}

    public void beforeExecute(Task task) {}

    public void afterExecute(Task task, TaskState state) {
        if (task.getProject().getGradle() == gradle) {
            logger.afterExecute();
        }
    }

    public void buildFinished(BuildResult result) {
        if (result.getGradle() == gradle) {
            gradle = null;
            logger.buildFinished();
        }
    }
}
