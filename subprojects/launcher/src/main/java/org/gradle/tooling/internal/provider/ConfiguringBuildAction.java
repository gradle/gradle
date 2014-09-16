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
import org.gradle.TaskExecutionRequest;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.launcher.cli.converter.PropertiesToStartParameterConverter;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is the action serialized from the tooling API provider across to the daemon. It takes care of setting up the start parameters on the daemon
 * side and then delegating to some other action to do the real work.
 *
 * @param <T> The result type.
 */
class ConfiguringBuildAction<T> implements BuildAction<T>, Serializable {
    private final BuildAction<? extends T> action;

    final StartParameter startParameter;

    public ConfiguringBuildAction(ProviderOperationParameters parameters, BuildAction<? extends T> action, Map<String, String> properties) {
        this.action = action;
        startParameter = configureStartParameter(parameters, properties);
    }

    private List<TaskExecutionRequest> unpack(final List<InternalLaunchable> launchables) {
        // Important that the launchables are unpacked on the client side, to avoid sending back any additional internal state that
        // the launchable may hold onto. For example, GradleTask implementations hold onto every task for every project in the build
        List<TaskExecutionRequest> requests = new ArrayList<TaskExecutionRequest>(launchables.size());
        for (InternalLaunchable launchable : launchables) {
            if (launchable instanceof TaskExecutionRequest) {
                TaskExecutionRequest originalLaunchable = (TaskExecutionRequest) launchable;
                TaskExecutionRequest launchableImpl = new DefaultTaskExecutionRequest(originalLaunchable.getArgs(), originalLaunchable.getProjectPath());
                requests.add(launchableImpl);
            } else {
                throw new InternalUnsupportedBuildArgumentException(
                        "Problem with provided launchable arguments: " + launchables + ". "
                                + "\nOnly objects from this provider can be built."
                );
            }
        }
        return requests;
    }

    private StartParameter configureStartParameter(ProviderOperationParameters parameters, Map<String, String> properties) {
        // Important that this is constructed on the client so that it has the right gradleHomeDir and other state internally
        StartParameter startParameter = new StartParameter();

        startParameter.setProjectDir(parameters.getProjectDir());
        if (parameters.getGradleUserHomeDir() != null) {
            startParameter.setGradleUserHomeDir(parameters.getGradleUserHomeDir());
        }

        List<InternalLaunchable> launchables = parameters.getLaunchables(null);
        if (launchables != null) {
            startParameter.setTaskRequests(unpack(launchables));
        } else if (parameters.getTasks() != null) {
            startParameter.setTaskNames(parameters.getTasks());
        }

        new PropertiesToStartParameterConverter().convert(properties, startParameter);

        List<String> arguments = parameters.getArguments(Collections.<String>emptyList());
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

        if (parameters.isSearchUpwards() != null) {
            startParameter.setSearchUpwards(parameters.isSearchUpwards());
        }

        if (parameters.getBuildLogLevel() != null) {
            startParameter.setLogLevel(parameters.getBuildLogLevel());
        }

        return startParameter;
    }

    public T run(BuildController buildController) {
        buildController.setStartParameter(startParameter);
        return action.run(buildController);
    }
}
