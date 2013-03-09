/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.launcher.daemon.configuration.GradleProperties;
import org.gradle.logging.ShowStacktrace;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

class ConfiguringBuildAction<T> implements GradleLauncherAction<T>, Serializable {
    private LogLevel buildLogLevel;
    private List<String> arguments;
    private List<String> tasks;
    private GradleLauncherAction<T> action;
    private File projectDirectory;
    private File gradleUserHomeDir;
    private Boolean searchUpwards;

    // Important that this is constructed on the client so that it has the right gradleHomeDir internally
    private final StartParameter startParameterTemplate = new StartParameter();
    private boolean configureOnDemand;

    public ConfiguringBuildAction() {}

    public ConfiguringBuildAction(ProviderOperationParameters parameters, GradleLauncherAction<T> action, GradleProperties gradleProperties) {
        this.configureOnDemand = gradleProperties.isConfigureOnDemand();
        this.gradleUserHomeDir = parameters.getGradleUserHomeDir();
        this.projectDirectory = parameters.getProjectDir();
        this.searchUpwards = parameters.isSearchUpwards();
        this.buildLogLevel = parameters.getBuildLogLevel();
        this.arguments = parameters.getArguments(Collections.<String>emptyList());
        this.tasks = parameters.getTasks();
        this.action = action;
    }

    StartParameter configureStartParameter() {
        StartParameter startParameter = startParameterTemplate.newInstance();
        startParameter.setProjectDir(projectDirectory);
        if (gradleUserHomeDir != null) {
            startParameter.setGradleUserHomeDir(gradleUserHomeDir);
        }
        if (searchUpwards != null) {
            startParameter.setSearchUpwards(searchUpwards);
        }

        if (tasks != null) {
            startParameter.setTaskNames(tasks);
        }

        startParameter.setConfigureOnDemand(configureOnDemand);

        if (arguments != null) {
            DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
            try {
                converter.convert(arguments, startParameter);
            } catch (CommandLineArgumentException e) {
                throw new InternalUnsupportedBuildArgumentException(
                    "Problem with provided build arguments: " + arguments + ". "
                    + "\n" + e.getMessage()
                    + "\nEither it is not a valid build option or it is not supported in the target Gradle version."
                    + "\nNot all of the Gradle command line options are supported build arguments."
                    + "\nExamples of supported build arguments: '--info', '-u', '-p'."
                    + "\nExamples of unsupported build options: '--daemon', '-?', '-v'."
                    + "\nPlease find more information in the javadoc for the BuildLauncher class.", e);
            }
        }

        if (buildLogLevel != null) {
            startParameter.setLogLevel(buildLogLevel);
        }

        startParameter.setShowStacktrace(ShowStacktrace.ALWAYS);
        return startParameter;
    }

    public BuildResult run(BuildController buildController) {
        buildController.setStartParameter(configureStartParameter());
        return action.run(buildController);
    }

    public T getResult() {
        return action.getResult();
    }
}
