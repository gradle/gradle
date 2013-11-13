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

package org.gradle.initialization.progress;

import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BuildProgressLogger {

    private final ProgressLoggerFactory progressLoggerFactory;

    private ProgressLogger buildProgress;
    private ProgressLogger configurationProgress;
    private Map<String, ProgressLogger> projectConfigurationProgress = new HashMap<String, ProgressLogger>();

    private ProgressFormatter buildProgressFormatter;
    private ProgressFormatter configurationProgressFormatter;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public void buildStarted() {
        buildProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
        buildProgress.setDescription("Initialize build");
        buildProgress.setShortDescription("Configuring");
        buildProgress.started();
    }

    public void projectsLoaded(int totalProjects) {
        configurationProgressFormatter = new SimpleProgressFormatter(totalProjects, "projects");
        configurationProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
        configurationProgress.setDescription("Configure projects");
        configurationProgress.setShortDescription(configurationProgressFormatter.getProgress());
        configurationProgress.started();
    }

    public void graphPopulated(int totalTasks) {
        configurationProgress.completed();
        configurationProgress = null;

        buildProgress.completed("Task graph ready");

        buildProgress = progressLoggerFactory.newOperation(BuildProgressLogger.class);
        buildProgressFormatter = new PercentageProgressFormatter("Building", totalTasks);
        buildProgress.setDescription("Execute tasks");
        buildProgress.setShortDescription(buildProgressFormatter.getProgress());
        buildProgress.started();
    }

    public void buildFinished() {
        buildProgress.completed();
        buildProgress = null;
        buildProgressFormatter = null;
        configurationProgress = null;
    }

    public void afterExecute() {
        buildProgress.progress(buildProgressFormatter.incrementAndGetProgress());
    }

    public void settingsEvaluated() {}

    public void beforeEvaluate(String projectPath) {
        if (configurationProgress != null) {
            ProgressLogger logger = progressLoggerFactory.newOperation(BuildProgressLogger.class);
            logger.setDescription("Configuring project " + projectPath);
            logger.setShortDescription(projectPath.equals(":") ? "root project" : projectPath);
            logger.started();
            projectConfigurationProgress.put(projectPath, logger);
        }
    }

    public void afterEvaluate(String projectPath) {
        if (configurationProgress != null) {
            ProgressLogger logger = projectConfigurationProgress.remove(projectPath);
            if (logger == null) {
                throw new IllegalStateException("Unexpected afterEvaluate event received without beforeEvaluate");
            }
            logger.completed();
            configurationProgress.progress(configurationProgressFormatter.incrementAndGetProgress());
        }
    }
}