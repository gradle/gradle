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

package org.gradle.internal.progress;

import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BuildProgressLogger implements LoggerProvider {

    private final ProgressLoggerProvider loggerProvider;

    private ProgressLogger buildProgress;
    private ProgressLogger configurationProgress;
    private Map<String, ProgressLogger> projectConfigurationProgress = new HashMap<String, ProgressLogger>();

    private ProgressFormatter buildProgressFormatter;
    private ProgressFormatter configurationProgressFormatter;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this(new ProgressLoggerProvider(progressLoggerFactory, BuildProgressLogger.class));
    }

    BuildProgressLogger(ProgressLoggerProvider loggerProvider) {
        this.loggerProvider = loggerProvider;
    }

    public void buildStarted() {
        buildProgress = loggerProvider.start("Initialize build", "Loading");
    }

    public void projectsLoaded(int totalProjects) {
        configurationProgressFormatter = new SimpleProgressFormatter(totalProjects, "projects");
        configurationProgress = loggerProvider.start("Configure projects", configurationProgressFormatter.getProgress());
    }

    public void graphPopulated(int totalTasks) {
        configurationProgress.completed();
        configurationProgress = null;

        buildProgress.completed("Task graph ready");

        buildProgressFormatter = new PercentageProgressFormatter("Building", totalTasks);
        buildProgress = loggerProvider.start("Execute tasks", buildProgressFormatter.getProgress());
    }

    public void buildFinished() {
        for (ProgressLogger l : projectConfigurationProgress.values()) {
            l.completed();
        }
        if (configurationProgress != null) {
            configurationProgress.completed();
        }
        buildProgress.completed();
        buildProgress = null;
        buildProgressFormatter = null;
        configurationProgress = null;
    }

    public void afterExecute() {
        buildProgress.progress(buildProgressFormatter.incrementAndGetProgress());
    }

    public void settingsEvaluated() {
        buildProgress.progress("Configuring");
    }

    public void beforeEvaluate(String projectPath) {
        if (configurationProgress != null) {
            ProgressLogger logger = loggerProvider.start("Configure project " + projectPath, projectPath.equals(":") ? "root project" : projectPath);
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

    public ProgressLogger getLogger() {
        if (buildProgress == null) {
            throw new IllegalStateException("Build logger is unavailable (it hasn't started or is already completed).");
        }
        return buildProgress;
    }
}