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

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.launcher.cli.converter.PropertiesToStartParameterConverter;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.internal.gradle.TaskListingLaunchable;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.UnsupportedMethodException;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ConfiguringBuildAction<T> implements BuildAction<T>, Serializable {
    private static List<String> computeTasks(ProviderOperationParameters parameters) {
        ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
        List<InternalLaunchable> launchables = null;
        try {
            launchables = parameters.getLaunchables();
        } catch (UnsupportedMethodException ume) {
            // older consumer version
        }
        if (launchables == null) {
            return parameters.getTasks();
        }
        List<String> allTasks = new ArrayList<String>();
        for (InternalLaunchable launchable : launchables) {
            if (launchable instanceof Task) {
                allTasks.add(((Task) launchable).getPath());
            } else if (launchable instanceof DefaultGradleTaskSelector) {
                allTasks.addAll(((DefaultGradleTaskSelector) launchable).getTasks());
            } else if (DefaultGradleTaskSelector.class.getName().equals(launchable.getClass().getName())) {
                TaskListingLaunchable selector = adapter.adapt(TaskListingLaunchable.class, launchable);
                for (String task : selector.getTasks()) {
                    allTasks.add(task);
                }
            } else {
                throw new GradleException("Only Task or TaskSelector instances are supported. Found " + launchable.getClass());
            }
        }
        return allTasks;
    }

    private LogLevel buildLogLevel;
    private List<String> arguments;
    private List<String> tasks;
    private BuildAction<? extends T> action;
    private File projectDirectory;
    private File gradleUserHomeDir;
    private Boolean searchUpwards;
    private Map<String, String> properties = new HashMap<String, String>();

    // Important that this is constructed on the client so that it has the right gradleHomeDir internally
    private final StartParameter startParameterTemplate = new StartParameter();

    public ConfiguringBuildAction() {}

    public ConfiguringBuildAction(ProviderOperationParameters parameters, BuildAction<? extends T> action, Map<String, String> properties) {
        this.properties.putAll(properties);
        this.gradleUserHomeDir = parameters.getGradleUserHomeDir();
        this.projectDirectory = parameters.getProjectDir();
        this.searchUpwards = parameters.isSearchUpwards();
        this.buildLogLevel = parameters.getBuildLogLevel();
        this.arguments = parameters.getArguments(Collections.<String>emptyList());
        this.tasks = computeTasks(parameters);
        this.action = action;
    }

    StartParameter configureStartParameter() {
        return configureStartParameter(new PropertiesToStartParameterConverter());
    }

    StartParameter configureStartParameter(PropertiesToStartParameterConverter propertiesToStartParameterConverter) {
        StartParameter startParameter = startParameterTemplate.newInstance();

        startParameter.setProjectDir(projectDirectory);
        if (gradleUserHomeDir != null) {
            startParameter.setGradleUserHomeDir(gradleUserHomeDir);
        }

        if (tasks != null) {
            startParameter.setTaskNames(tasks);
        }

        propertiesToStartParameterConverter.convert(properties, startParameter);

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

        if (searchUpwards != null) {
            startParameter.setSearchUpwards(searchUpwards);
        }

        if (buildLogLevel != null) {
            startParameter.setLogLevel(buildLogLevel);
        }

        return startParameter;
    }

    public T run(BuildController buildController) {
        buildController.setStartParameter(configureStartParameter());
        return action.run(buildController);
    }
}
