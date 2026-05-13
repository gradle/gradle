/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.api.Transformer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.invocation.BuildParameters;

import java.io.File;

public class StartParameterHelper {

    public static StartParameterInternal toStartParameter(BuildParameters params) {
        StartParameterInternal sp = new StartParameterInternal();

        // Logging
        sp.setLogLevel(params.getLogLevel());
        sp.setShowStacktrace(params.getShowStacktrace());
        sp.setConsoleOutput(params.getConsoleOutput());
        sp.setConsoleUnicodeSupport(params.getConsoleUnicodeSupport());
        sp.setWarningMode(params.getWarningMode());
        sp.setNonInteractive(params.isNonInteractive());

        // Parallelism
        sp.setParallelProjectExecutionEnabled(params.isParallelProjectExecutionEnabled());
        sp.setMaxWorkerCount(params.getMaxWorkerCount());

        // Welcome message
        sp.setWelcomeMessageConfiguration(new WelcomeMessageConfiguration(params.getWelcomeMessageDisplayMode()));

        // Log level override (TAPI-specific, highest priority)
        if (params.getLogLevelOverride() != null) {
            sp.setLogLevel(params.getLogLevelOverride());
        }

        // Tasks
        sp.setTaskRequests(params.getTaskRequests());

        // Layout
        if (params.getProjectDir() != null) {
            sp.setProjectDir(params.getProjectDir());
        }
        sp.setCurrentDir(params.getCurrentDir());
        sp.setGradleUserHomeDir(params.getGradleUserHomeDir());
        if (params.getGradleHomeDir() != null) {
            sp.setGradleHomeDir(params.getGradleHomeDir());
        }

        // Always-present collections
        sp.setProjectProperties(params.getProjectProperties());
        sp.setSystemPropertiesArgs(params.getSystemPropertiesArgs());

        // From ParsedOptions — apply if non-null, otherwise StartParameterInternal keeps its default
        Transformer<File, String> resolver = new BasicFileResolver(params.getCurrentDir());

        if (params.getProjectCacheDir() != null) {
            sp.setProjectCacheDir(resolver.transform(params.getProjectCacheDir()));
        }
        if (params.getInitScripts() != null) {
            for (String script : params.getInitScripts()) {
                sp.addInitScript(resolver.transform(script));
            }
        }
        if (params.getExcludedTaskNames() != null) {
            sp.setExcludedTaskNames(params.getExcludedTaskNames());
        }
        if (params.getIncludedBuilds() != null) {
            for (String includedBuild : params.getIncludedBuilds()) {
                sp.includeBuild(resolver.transform(includedBuild));
            }
        }
        if (params.getBuildProjectDependencies() != null) {
            sp.setBuildProjectDependencies(params.getBuildProjectDependencies());
        }
        if (params.getDryRun() != null) {
            sp.setDryRun(params.getDryRun());
        }
        if (params.getRerunTasks() != null) {
            sp.setRerunTasks(params.getRerunTasks());
        }
        if (params.getProfile() != null) {
            sp.setProfile(params.getProfile());
        }
        if (params.getContinueOnFailure() != null) {
            sp.setContinueOnFailure(params.getContinueOnFailure());
        }
        if (params.getOffline() != null) {
            sp.setOffline(params.getOffline());
        }
        if (params.getRefreshDependencies() != null) {
            sp.setRefreshDependencies(params.getRefreshDependencies());
        }
        if (params.getBuildCacheEnabled() != null) {
            sp.setBuildCacheEnabled(params.getBuildCacheEnabled());
        }
        if (params.getBuildCacheDebugLogging() != null) {
            sp.setBuildCacheDebugLogging(params.getBuildCacheDebugLogging());
        }
        if (params.getWatchFileSystemMode() != null) {
            sp.setWatchFileSystemMode(params.getWatchFileSystemMode());
        }
        if (params.getVfsVerboseLogging() != null) {
            sp.setVfsVerboseLogging(params.getVfsVerboseLogging());
        }
        if (params.getConfigurationCache() != null) {
            sp.setConfigurationCache(Option.Value.value(params.getConfigurationCache()));
        }
        if (params.getIsolatedProjects() != null) {
            sp.setIsolatedProjects(Option.Value.value(params.getIsolatedProjects()));
        }
        if (params.getConfigurationCacheProblems() != null) {
            sp.setConfigurationCacheProblems(params.getConfigurationCacheProblems());
        }
        if (params.getConfigurationCacheIgnoreInputsDuringStore() != null) {
            sp.setConfigurationCacheIgnoreInputsDuringStore(params.getConfigurationCacheIgnoreInputsDuringStore());
        }
        if (params.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners() != null) {
            sp.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(params.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners());
        }
        if (params.getConfigurationCacheMaxProblems() != null) {
            sp.setConfigurationCacheMaxProblems(params.getConfigurationCacheMaxProblems());
        }
        if (params.getConfigurationCacheIgnoredFileSystemCheckInputs() != null) {
            sp.setConfigurationCacheIgnoredFileSystemCheckInputs(params.getConfigurationCacheIgnoredFileSystemCheckInputs());
        }
        if (params.getConfigurationCacheDebug() != null) {
            sp.setConfigurationCacheDebug(params.getConfigurationCacheDebug());
        }
        if (params.getConfigurationCacheRecreateCache() != null) {
            sp.setConfigurationCacheRecreateCache(params.getConfigurationCacheRecreateCache());
        }
        if (params.getConfigurationCacheParallel() != null) {
            sp.setConfigurationCacheParallel(params.getConfigurationCacheParallel());
        }
        if (params.getConfigurationCacheReadOnly() != null) {
            sp.setConfigurationCacheReadOnly(params.getConfigurationCacheReadOnly());
        }
        if (params.getConfigurationCacheQuiet() != null) {
            sp.setConfigurationCacheQuiet(params.getConfigurationCacheQuiet());
        }
        if (params.getConfigurationCacheIntegrityCheckEnabled() != null) {
            sp.setConfigurationCacheIntegrityCheckEnabled(params.getConfigurationCacheIntegrityCheckEnabled());
        }
        if (params.getConfigurationCacheEntriesPerKey() != null) {
            sp.setConfigurationCacheEntriesPerKey(params.getConfigurationCacheEntriesPerKey());
        }
        if (params.getConfigurationCacheHeapDumpDir() != null) {
            sp.setConfigurationCacheHeapDumpDir(params.getConfigurationCacheHeapDumpDir());
        }
        if (params.getConfigurationCacheFineGrainedPropertyTracking() != null) {
            sp.setConfigurationCacheFineGrainedPropertyTracking(params.getConfigurationCacheFineGrainedPropertyTracking());
        }
        if (params.getConfigureOnDemand() != null) {
            sp.setConfigureOnDemand(params.getConfigureOnDemand());
        }
        if (params.getContinuous() != null) {
            sp.setContinuous(params.getContinuous());
        }
        if (params.getContinuousBuildQuietPeriod() != null) {
            sp.setContinuousBuildQuietPeriod(params.getContinuousBuildQuietPeriod());
        }
        if (params.getBuildScan() != null) {
            if (params.getBuildScan()) {
                sp.setBuildScan(true);
            } else {
                sp.setNoBuildScan(true);
            }
        }
        if (params.getWriteDependencyLocks() != null) {
            sp.setWriteDependencyLocks(params.getWriteDependencyLocks());
        }
        if (params.getWriteDependencyVerifications() != null) {
            sp.setWriteDependencyVerifications(params.getWriteDependencyVerifications());
        }
        if (params.getDependencyVerificationMode() != null) {
            sp.setDependencyVerificationMode(params.getDependencyVerificationMode());
        }
        if (params.getLockedDependenciesToUpdate() != null) {
            sp.setLockedDependenciesToUpdate(params.getLockedDependenciesToUpdate());
        }
        if (params.getRefreshKeys() != null) {
            sp.setRefreshKeys(params.getRefreshKeys());
        }
        if (params.getExportKeys() != null) {
            sp.setExportKeys(params.getExportKeys());
        }
        if (params.getPropertyUpgradeReportEnabled() != null) {
            sp.setPropertyUpgradeReportEnabled(params.getPropertyUpgradeReportEnabled());
        }
        if (params.getProblemReportGenerationEnabled() != null) {
            sp.enableProblemReportGeneration(params.getProblemReportGenerationEnabled());
        }
        if (params.getTaskGraph() != null) {
            sp.setTaskGraph(params.getTaskGraph());
        }
        if (params.getParallelToolingModelBuilding() != null) {
            sp.setParallelToolingModelBuilding(Option.Value.value(params.getParallelToolingModelBuilding()));
        }
        if (params.getDevelocityUrl() != null) {
            sp.setDevelocityUrl(params.getDevelocityUrl());
        }
        if (params.getDevelocityPluginVersion() != null) {
            sp.setDevelocityPluginVersion(params.getDevelocityPluginVersion());
        }

        return sp;
    }
}
