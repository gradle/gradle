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
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link org.gradle.BuildListener} which logs the build progress.
 */
public class BuildLogger implements BuildListener, TaskExecutionGraphListener {
    private final Logger logger;
    private final List<BuildListener> resultLoggers = new ArrayList<BuildListener>();

    public BuildLogger(Logger logger, StyledTextOutputFactory textOutputFactory, StartParameter startParameter, BuildRequestMetaData requestMetaData) {
        this.logger = logger;
        resultLoggers.add(new BuildExceptionReporter(textOutputFactory, startParameter, requestMetaData.getClient()));
        resultLoggers.add(new BuildResultLogger(textOutputFactory, requestMetaData.getBuildTimeClock()));
    }

    public void buildStarted(Gradle gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        logger.info("Starting Build");
        logger.debug("Gradle user home: {}", startParameter.getGradleUserHomeDir());
        logger.debug("Current dir: {}", startParameter.getCurrentDir());
        logger.debug("Settings file: {}", startParameter.getSettingsFile());
        logger.debug("Build file: {}", startParameter.getBuildFile());
    }

    public void settingsEvaluated(Settings settings) {
        SettingsInternal settingsInternal = (SettingsInternal) settings;
        logger.info("Settings evaluated using {}.",
                settingsInternal.getSettingsScript().getDisplayName());
    }

    public void projectsLoaded(Gradle gradle) {
        ProjectInternal projectInternal = (ProjectInternal) gradle.getRootProject();
        logger.info("Projects loaded. Root project using {}.",
                projectInternal.getBuildScriptSource().getDisplayName());
        logger.info("Included projects: {}", projectInternal.getAllprojects());
    }

    public void projectsEvaluated(Gradle gradle) {
        logger.info("All projects evaluated.");
    }

    public void graphPopulated(TaskExecutionGraph graph) {
        logger.info("Tasks to be executed: {}", graph.getAllTasks());
    }

    public void buildFinished(BuildResult result) {
        for (BuildListener logger : resultLoggers) {
            logger.buildFinished(result);
        }
    }
}
