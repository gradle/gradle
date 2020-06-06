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
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ProviderStartParameterConverter {

    private List<TaskExecutionRequest> unpack(final List<InternalLaunchable> launchables) {
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

    public StartParameterInternal toStartParameter(ProviderOperationParameters parameters, BuildLayoutResult buildLayout, AllProperties properties) {
        // Important that this is constructed on the client so that it has the right gradleHomeDir and other state internally
        StartParameterInternal startParameter = new StartParameterInternal();

        List<InternalLaunchable> launchables = parameters.getLaunchables();
        if (launchables != null) {
            startParameter.setTaskRequests(unpack(launchables));
        } else if (parameters.getTasks() != null) {
            startParameter.setTaskNames(parameters.getTasks());
        }

        List<String> arguments = parameters.getArguments();
        StartParameterConverter converter = new StartParameterConverter();
        CommandLineParser parser = new CommandLineParser();
        new InitialPropertiesConverter().configure(parser);
        new BuildLayoutConverter().configure(parser);
        converter.configure(parser);
        ParsedCommandLine parsedCommandLine;
        try {
            parsedCommandLine = parser.parse(arguments != null ? arguments : Collections.emptyList());
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
        converter.convert(parsedCommandLine, buildLayout, properties, startParameter);

        if (parameters.getBuildLogLevel() != null) {
            startParameter.setLogLevel(parameters.getBuildLogLevel());
        }

        return startParameter;
    }
}
