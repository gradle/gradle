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
    public static final String INITIALIZATION_PHASE_DESCRIPTION = "INITIALIZATION PHASE";
    public static final String CONFIGURATION_PHASE_DESCRIPTION = "CONFIGURATION PHASE";
    public static final String EXECUTION_PHASE_DESCRIPTION = "EXECUTION PHASE";

    private final ProgressLoggerProvider loggerProvider;
    private ProgressLogger buildProgress;

    public BuildProgressLogger(ProgressLoggerFactory progressLoggerFactory) {
        this(new ProgressLoggerProvider(progressLoggerFactory, BuildProgressLogger.class));
    }

    BuildProgressLogger(ProgressLoggerProvider loggerProvider) {
        this.loggerProvider = loggerProvider;
    }

    public void buildStarted() {
        buildProgress = loggerProvider.start(INITIALIZATION_PHASE_DESCRIPTION, null);
    }

    public void settingsEvaluated() {
        buildProgress.completed();
    }

    public void projectsLoaded(int totalProjects) {
        buildProgress = loggerProvider.start(CONFIGURATION_PHASE_DESCRIPTION, null);
    }

    public void beforeEvaluate(String projectPath) {}

    public void afterEvaluate(String projectPath) {}

    public void graphPopulated(int totalTasks) {
        buildProgress.completed();
        buildProgress = loggerProvider.start(EXECUTION_PHASE_DESCRIPTION, null);
    }

    public void beforeExecute() {}

    public void afterExecute() {}

    public void buildFinished() {
        buildProgress.completed();
        buildProgress = null;
    }

    public ProgressLogger getLogger() {
        if (buildProgress == null) {
            throw new IllegalStateException("Build logger is unavailable (it hasn't started or is already completed).");
        }
        return buildProgress;
    }
}
