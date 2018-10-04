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

import org.gradle.TaskExecutionRequest;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.launcher.cli.converter.PropertiesToStartParameterConverter;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ProviderStartParameterConverter {

    private List<TaskExecutionRequest> unpack(final List<InternalLaunchable> launchables, File projectDir) {
        // Important that the launchables are unpacked on the client side, to avoid sending back any additional internal state that
        // the launchable may hold onto. For example, GradleTask implementations hold onto every task for every project in the build
        List<TaskExecutionRequest> requests = new ArrayList<TaskExecutionRequest>(launchables.size());
        for (InternalLaunchable launchable : launchables) {
            if (launchable instanceof TaskExecutionRequest) {

                TaskExecutionRequest originalLaunchable = (TaskExecutionRequest) launchable;
                TaskExecutionRequest launchableImpl = new DefaultTaskExecutionRequest(originalLaunchable.getArgs(), originalLaunchable.getProjectPath(), originalLaunchable.getRootDir());
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

    public StartParameterInternal toStartParameter(ProviderOperationParameters parameters, Map<String, String> properties) {
        // Important that this is constructed on the client so that it has the right gradleHomeDir and other state internally
        StartParameterInternal startParameter = new StartParameterInternal();

        startParameter.setProjectDir(parameters.getProjectDir());
        if (parameters.getGradleUserHomeDir() != null) {
            startParameter.setGradleUserHomeDir(parameters.getGradleUserHomeDir());
        }

        List<InternalLaunchable> launchables = parameters.getLaunchables(null);
        if (launchables != null) {
            startParameter.setTaskRequests(unpack(launchables, parameters.getProjectDir()));
        } else if (parameters.getTasks() != null) {
            startParameter.setTaskNames(parameters.getTasks());
        }

        new PropertiesToStartParameterConverter().convert(properties, startParameter);

        List<String> arguments = parameters.getArguments();
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
                    + "\nExamples of supported build arguments: '--info', '-p'."
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
}
