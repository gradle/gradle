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

import org.gradle.TaskExecutionRequest;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.ProjectPropertiesCommandLineConverter;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.ParallelismBuildOptions;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.initialization.StartParameterBuildOptions.ParsedOptions;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.invocation.BuildParameters;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class StartParameterConverter {
    private final BuildOptionBackedConverter<WelcomeMessageConfiguration> welcomeMessageConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new WelcomeMessageBuildOptions());
    private final BuildOptionBackedConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new LoggingConfigurationBuildOptions());
    private final BuildOptionBackedConverter<ParallelismConfiguration> parallelConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new ParallelismBuildOptions());
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private final BuildOptionBackedConverter<ParsedOptions> buildOptionsConverter = new BuildOptionBackedConverter<>(new StartParameterBuildOptions());
    private final BuildOptionBackedConverter<Map<String, String>> toolchainOptionsConverter = new BuildOptionBackedConverter<>(ToolchainBuildOptions.forProjectProperties());

    public void configure(CommandLineParser parser) {
        welcomeMessageConfigurationCommandLineConverter.configure(parser);
        loggingConfigurationCommandLineConverter.configure(parser);
        parallelConfigurationCommandLineConverter.configure(parser);
        projectPropertiesCommandLineConverter.configure(parser);
        toolchainOptionsConverter.configure(parser);
        parser.allowMixedSubcommandsAndOptions();
        buildOptionsConverter.configure(parser);
    }

    public BuildParameters build(
        ParsedCommandLine parsedCommandLine,
        BuildLayoutResult buildLayout,
        AllProperties properties,
        Map<String, String> environmentVariables,
        @Nullable Collection<? extends TaskExecutionRequest> taskRequests,
        @Nullable LogLevel logLevelOverride
    ) throws CommandLineArgumentException {

        // Convert into standalone objects
        WelcomeMessageConfiguration welcomeMessage = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE);
        welcomeMessageConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, welcomeMessage);

        DefaultLoggingConfiguration loggingConfiguration = new DefaultLoggingConfiguration();
        loggingConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, loggingConfiguration);

        DefaultParallelismConfiguration parallelismConfiguration = new DefaultParallelismConfiguration();
        parallelConfigurationCommandLineConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, parallelismConfiguration);

        Map<String, String> projectProperties = new HashMap<>();
        projectPropertiesCommandLineConverter.convert(parsedCommandLine, projectProperties);

        Map<String, String> toolchainProperties = new HashMap<>();
        toolchainOptionsConverter.convert(parsedCommandLine, properties.getRequestedSystemProperties(), environmentVariables, toolchainProperties);

        ParsedOptions parsedOptions = new ParsedOptions();
        buildOptionsConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, parsedOptions);

        // Merge project properties: CLI -P first, then toolchain putIfAbsent
        for (Map.Entry<String, String> entry : toolchainProperties.entrySet()) {
            projectProperties.putIfAbsent(entry.getKey(), entry.getValue());
        }

        // Resolve task requests from CLI extra arguments if not explicitly provided
        List<TaskExecutionRequest> resolvedTaskRequests;
        if (taskRequests != null) {
            resolvedTaskRequests = new ArrayList<>(taskRequests);
        } else if (!parsedCommandLine.getExtraArguments().isEmpty()) {
            resolvedTaskRequests = Collections.singletonList(DefaultTaskExecutionRequest.of(parsedCommandLine.getExtraArguments()));
        } else {
            resolvedTaskRequests = Collections.emptyList();
        }

        return new BuildParameters(
            // From DefaultLoggingConfiguration
            loggingConfiguration.getLogLevel(),
            loggingConfiguration.getShowStacktrace(),
            loggingConfiguration.getConsoleOutput(),
            loggingConfiguration.getConsoleUnicodeSupport(),
            loggingConfiguration.getWarningMode(),
            loggingConfiguration.isNonInteractive(),
            // From DefaultParallelismConfiguration
            parallelismConfiguration.isParallelProjectExecutionEnabled(),
            parallelismConfiguration.getMaxWorkerCount(),
            // From WelcomeMessageConfiguration
            welcomeMessage.getWelcomeMessageDisplayMode(),
            // TAPI override
            logLevelOverride,
            // Tasks
            resolvedTaskRequests,
            // Layout
            buildLayout.getProjectDir(),
            buildLayout.getCurrentDir(),
            buildLayout.getGradleUserHomeDir(),
            null, // gradleHomeDir
            // Always-present collections
            projectProperties,
            new HashMap<>(properties.getRequestedSystemProperties()),
            // From ParsedOptions — pass through verbatim
            parsedOptions.getProjectCacheDir(),
            parsedOptions.getInitScripts(),
            parsedOptions.getExcludedTaskNames(),
            parsedOptions.getIncludedBuilds(),
            parsedOptions.getBuildProjectDependencies(),
            parsedOptions.getDryRun(),
            parsedOptions.getRerunTasks(),
            parsedOptions.getProfile(),
            parsedOptions.getContinueOnFailure(),
            parsedOptions.getOffline(),
            parsedOptions.getRefreshDependencies(),
            parsedOptions.getBuildCacheEnabled(),
            parsedOptions.getBuildCacheDebugLogging(),
            parsedOptions.getWatchFileSystemMode(),
            parsedOptions.getVfsVerboseLogging(),
            parsedOptions.getConfigurationCache(),
            parsedOptions.getIsolatedProjects(),
            parsedOptions.getConfigurationCacheProblems(),
            parsedOptions.getConfigurationCacheIgnoreInputsDuringStore(),
            parsedOptions.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners(),
            parsedOptions.getConfigurationCacheMaxProblems(),
            parsedOptions.getConfigurationCacheIgnoredFileSystemCheckInputs(),
            parsedOptions.getConfigurationCacheDebug(),
            parsedOptions.getConfigurationCacheRecreateCache(),
            parsedOptions.getConfigurationCacheParallel(),
            parsedOptions.getConfigurationCacheReadOnly(),
            parsedOptions.getConfigurationCacheQuiet(),
            parsedOptions.getConfigurationCacheIntegrityCheckEnabled(),
            parsedOptions.getConfigurationCacheEntriesPerKey(),
            parsedOptions.getConfigurationCacheHeapDumpDir(),
            parsedOptions.getConfigurationCacheFineGrainedPropertyTracking(),
            parsedOptions.getConfigureOnDemand(),
            parsedOptions.getContinuous(),
            parsedOptions.getContinuousBuildQuietPeriod(),
            parsedOptions.getBuildScan(),
            parsedOptions.getWriteDependencyLocks(),
            parsedOptions.getWriteDependencyVerifications(),
            parsedOptions.getDependencyVerificationMode(),
            parsedOptions.getLockedDependenciesToUpdate(),
            parsedOptions.getRefreshKeys(),
            parsedOptions.getExportKeys(),
            parsedOptions.getPropertyUpgradeReportEnabled(),
            parsedOptions.getProblemReportGeneration(),
            parsedOptions.getTaskGraph(),
            parsedOptions.getParallelToolingModelBuilding(),
            parsedOptions.getDevelocityUrl(),
            parsedOptions.getDevelocityPluginVersion()
        );
    }
}
