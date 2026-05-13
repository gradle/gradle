/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.action

import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.invocation.BuildParameters
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.internal.DefaultDebugOptions
import org.gradle.tooling.internal.provider.serialization.SerializedPayload

import java.beans.Introspector

class BuildActionSerializerTest extends SerializerSpec {

    static BuildParameters defaultBuildParameters() {
        new BuildParameters(
            // From DefaultLoggingConfiguration
            LogLevel.LIFECYCLE,
            ShowStacktrace.INTERNAL_EXCEPTIONS,
            ConsoleOutput.Auto,
            ConsoleUnicodeSupport.Auto,
            WarningMode.Summary,
            false,
            // From DefaultParallelismConfiguration
            false,
            Runtime.getRuntime().availableProcessors(),
            // From WelcomeMessageConfiguration
            WelcomeMessageDisplayMode.ONCE,
            // TAPI override
            null,
            // Tasks
            [],
            // Layout
            null,
            new File(".").absoluteFile,
            new File(System.getProperty("user.home"), ".gradle"),
            null,
            // Always-present collections
            [:],
            [:],
            // From ParsedOptions (all null = unset)
            null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null
        )
    }

    static BuildParameters buildParametersWithTasks(List<String> taskNames) {
        def params = defaultBuildParameters()
        new BuildParameters(
            params.logLevel, params.showStacktrace, params.consoleOutput,
            params.consoleUnicodeSupport, params.warningMode, params.nonInteractive,
            params.parallelProjectExecutionEnabled, params.maxWorkerCount,
            params.welcomeMessageDisplayMode, params.logLevelOverride,
            [DefaultTaskExecutionRequest.of(taskNames)],
            params.projectDir, params.currentDir, params.gradleUserHomeDir, params.gradleHomeDir,
            params.projectProperties, params.systemPropertiesArgs,
            params.projectCacheDir, params.initScripts, params.excludedTaskNames, params.includedBuilds,
            params.buildProjectDependencies, params.dryRun, params.rerunTasks, params.profile,
            params.continueOnFailure, params.offline, params.refreshDependencies,
            params.buildCacheEnabled, params.buildCacheDebugLogging,
            params.watchFileSystemMode, params.vfsVerboseLogging,
            params.configurationCache, params.isolatedProjects, params.configurationCacheProblems,
            params.configurationCacheIgnoreInputsDuringStore, params.configurationCacheIgnoreUnsupportedBuildEventsListeners,
            params.configurationCacheMaxProblems, params.configurationCacheIgnoredFileSystemCheckInputs,
            params.configurationCacheDebug, params.configurationCacheRecreateCache,
            params.configurationCacheParallel, params.configurationCacheReadOnly,
            params.configurationCacheQuiet, params.configurationCacheIntegrityCheckEnabled,
            params.configurationCacheEntriesPerKey, params.configurationCacheHeapDumpDir,
            params.configurationCacheFineGrainedPropertyTracking,
            params.configureOnDemand, params.continuous, params.continuousBuildQuietPeriod,
            params.buildScan, params.writeDependencyLocks,
            params.writeDependencyVerifications, params.dependencyVerificationMode,
            params.lockedDependenciesToUpdate,
            params.refreshKeys, params.exportKeys,
            params.propertyUpgradeReportEnabled, params.problemReportGenerationEnabled,
            params.taskGraph, params.parallelToolingModelBuilding,
            params.develocityUrl, params.develocityPluginVersion
        )
    }

    def "serializes ExecuteBuildAction with all defaults"() {
        def action = new ExecuteBuildAction(defaultBuildParameters())

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
    }

    def "serializes ExecuteBuildAction with non-defaults"() {
        def action = new ExecuteBuildAction(buildParametersWithTasks(['a', 'b']))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
        result.buildParameters.taskRequests.size() == 1
        result.buildParameters.taskRequests[0].args == ['a', 'b']
    }

    def "serializes #buildOptionName boolean build option"() {
        def params = defaultBuildParameters()

        expect:
        def action = new ExecuteBuildAction(params)
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ExecuteBuildAction
        result.buildParameters."${buildOptionName}" == params."${buildOptionName}"

        where:
        buildOptionName << Introspector.getBeanInfo(BuildParameters).propertyDescriptors
            .findAll { it.propertyType == boolean }
            .collect { it.name }
    }

    def "serializes BuildModelAction"() {
        def action = new BuildModelAction(buildParametersWithTasks(['a', 'b']), "model", true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof BuildModelAction
        result.buildParameters.taskRequests.size() == 1
        result.buildParameters.taskRequests[0].args == ['a', 'b']
        result.modelName == "model"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes ClientProvidedBuildAction"() {
        def action = new ClientProvidedBuildAction(buildParametersWithTasks(['a', 'b']), new SerializedPayload("12", []), true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ClientProvidedBuildAction
        result.buildParameters.taskRequests.size() == 1
        result.buildParameters.taskRequests[0].args == ['a', 'b']
        result.action.header == "12"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes ClientProvidedPhasedAction"() {
        def action = new ClientProvidedPhasedAction(buildParametersWithTasks(['a', 'b']), new SerializedPayload("12", []), true, new BuildEventSubscriptions([OperationType.TASK] as Set))

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof ClientProvidedPhasedAction
        result.buildParameters.taskRequests.size() == 1
        result.buildParameters.taskRequests[0].args == ['a', 'b']
        result.phasedAction.header == "12"
        result.runTasks
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }

    def "serializes TestExecutionRequestAction"() {
        def action = new TestExecutionRequestAction(new BuildEventSubscriptions([OperationType.TASK] as Set), defaultBuildParameters(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), new DefaultDebugOptions(), Collections.emptyMap(), false, Collections.emptyList())

        expect:
        def result = serialize(action, BuildActionSerializer.create())
        result instanceof TestExecutionRequestAction
        result.clientSubscriptions.operationTypes == [OperationType.TASK] as Set
    }
}
