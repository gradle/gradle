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

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public class BuildProgressLogger implements LoggerProvider {
    public static final String INITIALIZATION_PHASE_DESCRIPTION = "Initializing build";
    public static final String INITIALIZATION_PHASE_SHORT_DESCRIPTION = "INITIALIZING";
    public static final String CONFIGURATION_PHASE_DESCRIPTION = "Configuring projects";
    public static final String CONFIGURATION_PHASE_SHORT_DESCRIPTION = "CONFIGURING";
    public static final String EXECUTION_PHASE_DESCRIPTION = "Executing tasks";
    public static final String EXECUTION_PHASE_SHORT_DESCRIPTION = "EXECUTING";
    public static final String WAITING_PHASE_DESCRIPTION = "WAITING";

    private final ProgressLoggerProvider loggerProvider;
    private boolean rootBuildInitComplete;
    private boolean rootTaskGraphPopulated;

    private ProgressLogger buildProgress;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this(new ProgressLoggerProvider(progressLoggerFactory, BuildProgressLogger.class));
    }

    BuildProgressLogger(ProgressLoggerProvider loggerProvider) {
        this.loggerProvider = loggerProvider;
    }

    public void buildStarted() {
        buildProgress = loggerProvider.start(INITIALIZATION_PHASE_DESCRIPTION, INITIALIZATION_PHASE_SHORT_DESCRIPTION, 0);
    }

    public void settingsEvaluated() {
        buildProgress.completed();
        rootBuildInitComplete = true;
    }

    public void projectsLoaded(int totalProjects) {
        buildProgress = loggerProvider.start(CONFIGURATION_PHASE_DESCRIPTION, CONFIGURATION_PHASE_SHORT_DESCRIPTION, totalProjects);
    }

    public void beforeEvaluate(String projectPath) {}

    public void afterEvaluate(String projectPath) {
        if (!rootTaskGraphPopulated) {
            buildProgress.progress("", false);
        }
    }

    public void graphPopulated(int totalTasks) {
        rootTaskGraphPopulated = true;
        buildProgress.completed();
        buildProgress = loggerProvider.start(EXECUTION_PHASE_DESCRIPTION, EXECUTION_PHASE_SHORT_DESCRIPTION, totalTasks);
    }

    public void nestedTaskGraphPopulated(int totalTasks) {
        if (!rootBuildInitComplete) {
            buildProgress.completed();
            buildProgress = loggerProvider.start(INITIALIZATION_PHASE_DESCRIPTION, INITIALIZATION_PHASE_SHORT_DESCRIPTION, totalTasks);
        }
    }

    public void beforeExecute() {}

    public void afterExecute(boolean taskFailed) {
        buildProgress.progress("", taskFailed);
    }

    public void afterNestedExecute(boolean taskFailed) {
        if (!rootBuildInitComplete) {
            afterExecute(taskFailed);
        }
    }

    public void beforeComplete() {
        if (buildProgress != null) {
            buildProgress.completed(WAITING_PHASE_DESCRIPTION, false);
        }
        buildProgress = null;
    }

    public ProgressLogger getLogger() {
        if (buildProgress == null) {
            throw new IllegalStateException("Build logger is unavailable (it hasn't started or is already completed).");
        }
        return buildProgress;
    }
}
