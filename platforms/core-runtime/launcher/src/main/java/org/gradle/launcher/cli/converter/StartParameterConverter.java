/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.launcher.cli.converter;

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.ProjectPropertiesCommandLineConverter;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.ParallelismBuildOptions;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions;

import java.util.Map;


public class StartParameterConverter {
    /**
     * Converts the arguments of the command-line client, for which agent mode is fully supported.
     */
    public static StartParameterConverter forCommandLine() {
        return new StartParameterConverter(true);
    }

    /**
     * Converts the arguments of a Tooling API client. Agent mode is only recorded as requested, but has no effect,
     * as such a client owns the output streams and receives the build's outcome as structured events anyway.
     */
    public static StartParameterConverter forToolingApi() {
        return new StartParameterConverter(false);
    }

    private final boolean agentModeSupported;

    private final BuildOptionBackedConverter<WelcomeMessageConfiguration> welcomeMessageConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new WelcomeMessageBuildOptions());
    private final BuildOptionBackedConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new LoggingConfigurationBuildOptions());
    private final BuildOptionBackedConverter<ParallelismConfiguration> parallelConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new ParallelismBuildOptions());
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private final BuildOptionBackedConverter<StartParameterInternal> buildOptionsConverter = new BuildOptionBackedConverter<>(new StartParameterBuildOptions());
    private final BuildOptionBackedConverter<StartParameter> toolchainOptionsConverter = new BuildOptionBackedConverter<>(ToolchainBuildOptions.forStartParameter());
    private final AgentModeResolver agentModeResolver = new AgentModeResolver();

    private StartParameterConverter(boolean agentModeSupported) {
        this.agentModeSupported = agentModeSupported;
    }

    public void configure(CommandLineParser parser) {
        welcomeMessageConfigurationCommandLineConverter.configure(parser);
        loggingConfigurationCommandLineConverter.configure(parser);
        parallelConfigurationCommandLineConverter.configure(parser);
        projectPropertiesCommandLineConverter.configure(parser);
        toolchainOptionsConverter.configure(parser);
        parser.allowMixedSubcommandsAndOptions();
        buildOptionsConverter.configure(parser);
    }

    public StartParameterInternal convert(ParsedCommandLine parsedCommandLine, BuildLayoutResult buildLayout, AllProperties properties, Map<String, String> environmentVariables, StartParameterInternal startParameter) throws CommandLineArgumentException {
        buildLayout.applyTo(startParameter);

        boolean agentMode = agentModeResolver.resolve(parsedCommandLine, properties.getProperties(), environmentVariables).isEnabled();
        if (agentMode && agentModeSupported) {
            AgentModeResolver.applyDefaultsTo(startParameter);
        }

        welcomeMessageConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter.getWelcomeMessageConfiguration());
        loggingConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter);
        parallelConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter);

        startParameter.getSystemPropertiesArgs().putAll(properties.getRequestedSystemProperties());

        projectPropertiesCommandLineConverter.convert(parsedCommandLine, startParameter.getProjectPropertiesUntracked());
        toolchainOptionsConverter.convert(parsedCommandLine, properties.getRequestedSystemProperties(), environmentVariables, startParameter);

        if (!parsedCommandLine.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(parsedCommandLine.getExtraArguments());
        }

        buildOptionsConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, startParameter);

        // Agent mode does not follow the usual precedence of the build options, and owns the console output
        startParameter.setAgentMode(agentMode);
        if (agentMode && agentModeSupported) {
            startParameter.setConsoleOutput(ConsoleOutput.Plain);
        }

        return startParameter;
    }
}
