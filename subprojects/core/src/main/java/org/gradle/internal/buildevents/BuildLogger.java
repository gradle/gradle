/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.InternalListener;
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.time.Clock;

/**
 * A {@link org.gradle.BuildListener} which logs the build progress.
 */
public class BuildLogger implements BuildListener, TaskExecutionGraphListener, InternalListener {
    private final Logger logger;
    private final BuildExceptionReporter exceptionReporter;
    private final BuildResultLogger resultLogger;
    private String action;

    public BuildLogger(Logger logger, StyledTextOutputFactory textOutputFactory, StartParameter startParameter, BuildRequestMetaData requestMetaData, BuildStartedTime buildStartedTime, Clock clock) {
        this.logger = logger;
        exceptionReporter = new BuildExceptionReporter(textOutputFactory, startParameter, requestMetaData.getClient());
        resultLogger = new BuildResultLogger(textOutputFactory, buildStartedTime, clock, new TersePrettyDurationFormatter());
    }

    @Override
    public void buildStarted(Gradle gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        logger.info("Starting Build");
        if (logger.isDebugEnabled()) {
            logger.debug("Gradle user home: {}", startParameter.getGradleUserHomeDir());
            logger.debug("Current dir: {}", startParameter.getCurrentDir());
            logger.debug("Settings file: {}", startParameter.getSettingsFile());
            logger.debug("Build file: {}", startParameter.getBuildFile());
        }
    }

    @Override
    public void settingsEvaluated(Settings settings) {
        SettingsInternal settingsInternal = (SettingsInternal) settings;
        if (logger.isInfoEnabled()) {
            logger.info("Settings evaluated using {}.",
                settingsInternal.getSettingsScript().getDisplayName());
        }
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        if (logger.isInfoEnabled()) {
            ProjectInternal projectInternal = (ProjectInternal) gradle.getRootProject();
            logger.info("Projects loaded. Root project using {}.",
                projectInternal.getBuildScriptSource().getDisplayName());
            logger.info("Included projects: {}", projectInternal.getAllprojects());
        }
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        logger.info("All projects evaluated.");
    }

    @Override
    public void graphPopulated(TaskExecutionGraph graph) {
        if (logger.isInfoEnabled()) {
            logger.info("Tasks to be executed: {}", graph.getAllTasks());
        }
    }

    @Override
    public void buildFinished(BuildResult result) {
        this.action = result.getAction();
    }

    public void logResult(Throwable buildFailure) {
        if (action == null) {
            // This logger has been replaced (for example using `Gradle.useLogger()`), so don't log anything
            return;
        }
        BuildResult buildResult = new BuildResult(action, null, buildFailure);
        exceptionReporter.buildFinished(buildResult);
        resultLogger.buildFinished(buildResult);
    }
}
