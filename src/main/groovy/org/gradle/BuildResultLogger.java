/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle;

import org.slf4j.Logger;
import org.gradle.util.Clock;
import org.gradle.api.logging.Logging;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Build;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * A {@link BuildListener} which logs the final result of the build.
 */
public class BuildResultLogger implements BuildListener {
    private final Logger logger;
    private final Clock buildTimeClock;

    public BuildResultLogger(Logger logger) {
        this.logger = logger;
        buildTimeClock = new Clock();
    }

    public void buildStarted(StartParameter startParameter) {
        logger.info("Starting Build");
        logger.debug("Gradle home: " + startParameter.getGradleHomeDir());
        logger.debug("Gradle user home: " + startParameter.getGradleUserHomeDir());
        logger.debug("Project dir: " + startParameter.getCurrentDir());
        logger.debug("Build file name: " + startParameter.getBuildFileName());
        logger.debug("Select default project: " + startParameter.getDefaultProjectSelector().getDescription());
        logger.debug("Plugin properties: " + startParameter.getPluginPropertiesFile());
        logger.debug("Default imports file: " + startParameter.getDefaultImportsFile());
    }

    public void settingsEvaluated(Settings settings) {
        SettingsInternal settingsInternal = (SettingsInternal) settings;
        logger.info(String.format("Settings evaluated using %s.",
                settingsInternal.getSettingsScript().getDescription()));
    }

    public void projectsLoaded(Build build) {
        ProjectInternal projectInternal = (ProjectInternal) build.getRootProject();
        logger.info(String.format("Projects loaded. Root project using %s.",
                projectInternal.getBuildScriptSource().getDescription()));
        logger.info(String.format("Included projects: %s", projectInternal.getAllprojects()));
    }

    public void projectsEvaluated(Build build) {
        logger.info("All projects evaluated.");
    }

    public void taskGraphPopulated(TaskExecutionGraph graph) {
        logger.info(String.format("Tasks to be executed: %s", graph.getAllTasks()));
    }

    public void buildFinished(BuildResult result) {
        if (result.getFailure() == null) {
            logger.info(Logging.LIFECYCLE, String.format("%nBUILD SUCCESSFUL%n"));
        } else {
            logger.error(String.format("%nBUILD FAILED%n"));
        }
        logger.info(Logging.LIFECYCLE, String.format("Total time: %s", buildTimeClock.getTime()));
    }
}
