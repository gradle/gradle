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
import org.gradle.api.Transformer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.BasicFileResolver;
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
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class StartParameterConverter {
    private final BuildOptionBackedConverter<WelcomeMessageConfiguration> welcomeMessageConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new WelcomeMessageBuildOptions());
    private final BuildOptionBackedConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new LoggingConfigurationBuildOptions());
    private final BuildOptionBackedConverter<ParallelismConfiguration> parallelConfigurationCommandLineConverter = new BuildOptionBackedConverter<>(new ParallelismBuildOptions());
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private final BuildOptionBackedConverter<StartParameterBuildOptions.ParsedOptions> buildOptionsConverter = new BuildOptionBackedConverter<>(new StartParameterBuildOptions());
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

    public StartParameterInternal build(
        ParsedCommandLine parsedCommandLine,
        BuildLayoutResult buildLayout,
        AllProperties properties,
        Map<String, String> environmentVariables,
        @Nullable Collection<? extends TaskExecutionRequest> taskRequests,
        @Nullable LogLevel logLevelOverride
    ) throws CommandLineArgumentException {
        StartParameterInternal startParameter = new StartParameterInternal();

        if (taskRequests != null) {
            startParameter.setTaskRequests(taskRequests);
        }

        buildLayout.applyTo(startParameter);

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

        StartParameterBuildOptions.ParsedOptions parsedOptions = new StartParameterBuildOptions.ParsedOptions();
        buildOptionsConverter.convert(parsedCommandLine, properties.getProperties(), environmentVariables, parsedOptions);

        applyWelcomeMessage(welcomeMessage, startParameter);
        applyLoggingConfiguration(loggingConfiguration, startParameter);
        applyParallelismConfiguration(parallelismConfiguration, startParameter);

        startParameter.getSystemPropertiesArgs().putAll(properties.getRequestedSystemProperties());

        Map<String, String> mergedProjectProperties = startParameter.getProjectPropertiesUntracked();
        mergedProjectProperties.putAll(projectProperties);
        for (Map.Entry<String, String> entry : toolchainProperties.entrySet()) {
            mergedProjectProperties.putIfAbsent(entry.getKey(), entry.getValue());
        }

        if (taskRequests == null && !parsedCommandLine.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(parsedCommandLine.getExtraArguments());
        }

        applyParsedOptions(parsedOptions, startParameter);

        if (logLevelOverride != null) {
            startParameter.setLogLevel(logLevelOverride);
        }

        return startParameter;
    }

    private static void applyWelcomeMessage(WelcomeMessageConfiguration source, StartParameterInternal target) {
        target.getWelcomeMessageConfiguration().setWelcomeMessageDisplayMode(source.getWelcomeMessageDisplayMode());
    }

    private static void applyLoggingConfiguration(DefaultLoggingConfiguration source, StartParameterInternal target) {
        target.setLogLevel(source.getLogLevel());
        target.setShowStacktrace(source.getShowStacktrace());
        target.setConsoleOutput(source.getConsoleOutput());
        target.setConsoleUnicodeSupport(source.getConsoleUnicodeSupport());
        target.setWarningMode(source.getWarningMode());
        if (source.isNonInteractive()) {
            target.setNonInteractive(true);
        }
    }

    private static void applyParallelismConfiguration(DefaultParallelismConfiguration source, StartParameterInternal target) {
        target.setParallelProjectExecutionEnabled(source.isParallelProjectExecutionEnabled());
        target.setMaxWorkerCount(source.getMaxWorkerCount());
    }

    private static void applyParsedOptions(StartParameterBuildOptions.ParsedOptions source, StartParameterInternal target) {
        Transformer<File, String> resolver = new BasicFileResolver(target.getCurrentDir());

        if (source.getProjectCacheDir() != null) {
            target.setProjectCacheDir(resolver.transform(source.getProjectCacheDir()));
        }
        if (source.getRerunTasks() != null) {
            target.setRerunTasks(source.getRerunTasks());
        }
        if (source.getProfile() != null) {
            target.setProfile(source.getProfile());
        }
        if (source.getContinueOnFailure() != null) {
            target.setContinueOnFailure(source.getContinueOnFailure());
        }
        if (source.getOffline() != null) {
            target.setOffline(source.getOffline());
        }
        if (source.getRefreshDependencies() != null) {
            target.setRefreshDependencies(source.getRefreshDependencies());
        }
        if (source.getDryRun() != null) {
            target.setDryRun(source.getDryRun());
        }
        if (source.getContinuous() != null) {
            target.setContinuous(source.getContinuous());
        }
        if (source.getContinuousBuildQuietPeriod() != null) {
            target.setContinuousBuildQuietPeriod(source.getContinuousBuildQuietPeriod());
        }
        if (source.getBuildProjectDependencies() != null) {
            target.setBuildProjectDependencies(source.getBuildProjectDependencies());
        }
        if (source.getInitScripts() != null) {
            for (String script : source.getInitScripts()) {
                target.addInitScript(resolver.transform(script));
            }
        }
        if (source.getExcludedTaskNames() != null) {
            target.setExcludedTaskNames(source.getExcludedTaskNames());
        }
        if (source.getIncludedBuilds() != null) {
            for (String includedBuild : source.getIncludedBuilds()) {
                target.includeBuild(resolver.transform(includedBuild));
            }
        }
        if (source.getConfigureOnDemand() != null) {
            target.setConfigureOnDemand(source.getConfigureOnDemand());
        }
        if (source.getBuildCacheEnabled() != null) {
            target.setBuildCacheEnabled(source.getBuildCacheEnabled());
        }
        if (source.getBuildCacheDebugLogging() != null) {
            target.setBuildCacheDebugLogging(source.getBuildCacheDebugLogging());
        }
        if (source.getWatchFileSystemMode() != null) {
            target.setWatchFileSystemMode(source.getWatchFileSystemMode());
        }
        if (source.getVfsVerboseLogging() != null) {
            target.setVfsVerboseLogging(source.getVfsVerboseLogging());
        }
        if (source.getBuildScan() != null) {
            if (source.getBuildScan()) {
                target.setBuildScan(true);
            } else {
                target.setNoBuildScan(true);
            }
        }
        if (source.getDevelocityUrl() != null) {
            target.setDevelocityUrl(source.getDevelocityUrl());
        }
        if (source.getDevelocityPluginVersion() != null) {
            target.setDevelocityPluginVersion(source.getDevelocityPluginVersion());
        }
        if (source.getWriteDependencyLocks() != null) {
            target.setWriteDependencyLocks(source.getWriteDependencyLocks());
        }
        if (source.getWriteDependencyVerifications() != null) {
            target.setWriteDependencyVerifications(source.getWriteDependencyVerifications());
        }
        if (source.getDependencyVerificationMode() != null) {
            target.setDependencyVerificationMode(source.getDependencyVerificationMode());
        }
        if (source.getLockedDependenciesToUpdate() != null) {
            target.setLockedDependenciesToUpdate(source.getLockedDependenciesToUpdate());
        }
        if (source.getRefreshKeys() != null) {
            target.setRefreshKeys(source.getRefreshKeys());
        }
        if (source.getExportKeys() != null) {
            target.setExportKeys(source.getExportKeys());
        }
        if (source.getConfigurationCacheProblems() != null) {
            target.setConfigurationCacheProblems(source.getConfigurationCacheProblems());
        }
        if (source.getConfigurationCache() != null) {
            target.setConfigurationCache(Option.Value.value(source.getConfigurationCache()));
        }
        if (source.getConfigurationCacheIgnoreInputsDuringStore() != null) {
            target.setConfigurationCacheIgnoreInputsDuringStore(source.getConfigurationCacheIgnoreInputsDuringStore());
        }
        if (source.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners() != null) {
            target.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(source.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners());
        }
        if (source.getConfigurationCacheMaxProblems() != null) {
            target.setConfigurationCacheMaxProblems(source.getConfigurationCacheMaxProblems());
        }
        if (source.getConfigurationCacheIgnoredFileSystemCheckInputs() != null) {
            target.setConfigurationCacheIgnoredFileSystemCheckInputs(source.getConfigurationCacheIgnoredFileSystemCheckInputs());
        }
        if (source.getConfigurationCacheDebug() != null) {
            target.setConfigurationCacheDebug(source.getConfigurationCacheDebug());
        }
        if (source.getConfigurationCacheParallel() != null) {
            target.setConfigurationCacheParallel(source.getConfigurationCacheParallel());
        }
        if (source.getConfigurationCacheReadOnly() != null) {
            target.setConfigurationCacheReadOnly(source.getConfigurationCacheReadOnly());
        }
        if (source.getConfigurationCacheRecreateCache() != null) {
            target.setConfigurationCacheRecreateCache(source.getConfigurationCacheRecreateCache());
        }
        if (source.getConfigurationCacheQuiet() != null) {
            target.setConfigurationCacheQuiet(source.getConfigurationCacheQuiet());
        }
        if (source.getConfigurationCacheIntegrityCheckEnabled() != null) {
            target.setConfigurationCacheIntegrityCheckEnabled(source.getConfigurationCacheIntegrityCheckEnabled());
        }
        if (source.getConfigurationCacheEntriesPerKey() != null) {
            target.setConfigurationCacheEntriesPerKey(source.getConfigurationCacheEntriesPerKey());
        }
        if (source.getConfigurationCacheHeapDumpDir() != null) {
            target.setConfigurationCacheHeapDumpDir(source.getConfigurationCacheHeapDumpDir());
        }
        if (source.getConfigurationCacheFineGrainedPropertyTracking() != null) {
            target.setConfigurationCacheFineGrainedPropertyTracking(source.getConfigurationCacheFineGrainedPropertyTracking());
        }
        if (source.getIsolatedProjects() != null) {
            target.setIsolatedProjects(Option.Value.value(source.getIsolatedProjects()));
        }
        if (source.getProblemReportGeneration() != null) {
            target.enableProblemReportGeneration(source.getProblemReportGeneration());
        }
        if (source.getPropertyUpgradeReportEnabled() != null) {
            target.setPropertyUpgradeReportEnabled(source.getPropertyUpgradeReportEnabled());
        }
        if (source.getTaskGraph() != null) {
            target.setTaskGraph(source.getTaskGraph());
        }
        if (source.getParallelToolingModelBuilding() != null) {
            target.setParallelToolingModelBuilding(Option.Value.value(source.getParallelToolingModelBuilding()));
        }
    }
}
