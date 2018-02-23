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
package org.gradle.internal.buildevents;

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ProjectEvaluationLogger implements ProjectEvaluationListener {
    private final Map<String, ProgressLogger> currentProjects = new HashMap<String, ProgressLogger>();
    private final ProgressLoggerFactory progressLoggerFactory;

    public ProjectEvaluationLogger(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @Override
    public void beforeEvaluate(Project project) {
        // --configuration-on-demand can sometimes cause projects to be configured after execution phase has started
        // see ConfigurationOnDemandIntegrationTest
        final String projectPath = project.getPath().equals(":") ? "root project" : project.getPath();
        ProgressLogger currentTask = progressLoggerFactory
            .newOperation(ProjectEvaluationLogger.class)
            .start("Configuring ".concat(projectPath), projectPath);
        currentProjects.put(project.getPath(), currentTask);
    }

    @Override
    public void afterEvaluate(Project project, ProjectState state) {
        ProgressLogger logger = currentProjects.remove(project.getPath());
        if (logger != null) {
            logger.completed();
        }
    }
}
