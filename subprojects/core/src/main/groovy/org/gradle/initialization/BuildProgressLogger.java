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

package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.progress.DefaultProgressFormatter;
import org.gradle.initialization.progress.ProgressFormatter;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

class BuildProgressLogger extends BuildAdapter implements TaskExecutionGraphListener, TaskExecutionListener, ProjectEvaluationListener {

    private final ProgressLoggerFactory progressLoggerFactory;
    private Gradle gradle;

    private ProgressLogger buildProgress;
    private ProgressLogger configurationProgress;
    private Map<String, ProgressLogger> projectConfigurationProgress = new HashMap<String, ProgressLogger>();

    private ProgressFormatter buildProgressFormatter;
    private int configuredProjects;
    private int totalProjects;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @Override
    public void buildStarted(Gradle gradle) {
        if (gradle.getParent() == null) { //TODO SF push the logic of filtering nested builds out of here
            buildProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
            buildProgress.setDescription("Initialize build");
            buildProgress.setShortDescription("Configuring");
            buildProgress.started();
            this.gradle = gradle;
        }
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        if (gradle.getParent() == null) {
            configuredProjects = 0;
            totalProjects = gradle.getRootProject().getAllprojects().size();
            configurationProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
            configurationProgress.setDescription("Configure projects");
            configurationProgress.setShortDescription("0/" + totalProjects + " projects");
            configurationProgress.started();
        }
    }

    public void graphPopulated(TaskExecutionGraph graph) {
        if (graph == gradle.getTaskGraph()) {
            configurationProgress.completed();
            configurationProgress = null;

            buildProgress.completed("Task graph ready");

            buildProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
            buildProgress.setDescription("Execute tasks");
            String desc = "Building";
            buildProgress.setShortDescription(desc + " 0%");
            buildProgressFormatter = new DefaultProgressFormatter(desc, graph.getAllTasks().size());
            buildProgress.started();
        }
    }

    @Override
    public void buildFinished(BuildResult result) {
        if (result.getGradle() == gradle) {
            buildProgress.completed();
            buildProgress = null;
            gradle = null;
            buildProgressFormatter = null;
            configurationProgress = null;
        }
    }

    public void beforeExecute(Task task) {
    }

    public void afterExecute(Task task, TaskState state) {
        if (task.getProject().getGradle() == gradle) {
            buildProgress.progress(buildProgressFormatter.progress());
        }
    }

    public void beforeEvaluate(Project project) {
        if (project.getGradle() == gradle && configurationProgress != null) {
            ProgressLogger logger = progressLoggerFactory.newOperation(BuildProgressLogger.class);
            logger.setDescription("Configuring project " + project.getPath());
            logger.setShortDescription(project.getPath().equals(":") ? "root project" : project.getPath());
            logger.started();
            projectConfigurationProgress.put(project.getPath(), logger);
        }
    }

    public void afterEvaluate(Project project, ProjectState state) {
        if (project.getGradle() == gradle && configurationProgress != null) {
            ProgressLogger logger = projectConfigurationProgress.remove(project.getPath());
            if (logger == null) {
                throw new IllegalStateException("Unexpected afterEvaluate event received without beforeEvaluate");
            }
            logger.completed();
            configurationProgress.progress(++configuredProjects + "/" + totalProjects + " projects");
        }
    }
}