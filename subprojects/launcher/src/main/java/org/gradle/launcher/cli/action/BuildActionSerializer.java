/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.launcher.cli.action;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER;

public class BuildActionSerializer {
    public static Serializer<BuildAction> create() {
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();
        registry.register(ExecuteBuildAction.class, new ExecuteBuildActionSerializer());
        // Use Java serialization for everything else
        registry.useJavaSerialization(BuildAction.class);
        return registry.build(BuildAction.class);
    }

    private static class ExecuteBuildActionSerializer implements Serializer<ExecuteBuildAction> {
        private final Serializer<LogLevel> logLevelSerializer;
        private final Serializer<ShowStacktrace> showStacktraceSerializer;
        private final Serializer<ConsoleOutput> consoleOutputSerializer;
        private final Serializer<WarningMode> warningModeSerializer;
        private final Serializer<File> nullableFileSerializer = new NullableFileSerializer();
        private final Serializer<List<String>> stringListSerializer = new ListSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);
        private final Serializer<List<File>> fileListSerializer = new ListSerializer<>(BaseSerializerFactory.FILE_SERIALIZER);
        private final Serializer<Set<String>> stringSetSerializer = new SetSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);

        ExecuteBuildActionSerializer() {
            BaseSerializerFactory serializerFactory = new BaseSerializerFactory();
            logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class);
            showStacktraceSerializer = serializerFactory.getSerializerFor(ShowStacktrace.class);
            consoleOutputSerializer = serializerFactory.getSerializerFor(ConsoleOutput.class);
            warningModeSerializer = serializerFactory.getSerializerFor(WarningMode.class);
        }

        @Override
        public void write(Encoder encoder, ExecuteBuildAction action) throws Exception {
            StartParameterInternal startParameter = action.getStartParameter();

            // Log configuration
            logLevelSerializer.write(encoder, startParameter.getLogLevel());
            showStacktraceSerializer.write(encoder, startParameter.getShowStacktrace());
            consoleOutputSerializer.write(encoder, startParameter.getConsoleOutput());
            warningModeSerializer.write(encoder, startParameter.getWarningMode());

            // Parallel configuration
            encoder.writeBoolean(startParameter.isParallelProjectExecutionEnabled());
            encoder.writeSmallInt(startParameter.getMaxWorkerCount());

            // Tasks
            writeTaskRequests(encoder, startParameter.getTaskRequests());
            stringSetSerializer.write(encoder, startParameter.getExcludedTaskNames());

            // Layout
            nullableFileSerializer.write(encoder, startParameter.getBuildFile());
            nullableFileSerializer.write(encoder, startParameter.getProjectDir());
            nullableFileSerializer.write(encoder, startParameter.getSettingsFile());
            FILE_SERIALIZER.write(encoder, startParameter.getCurrentDir());
            FILE_SERIALIZER.write(encoder, startParameter.getGradleUserHomeDir());
            nullableFileSerializer.write(encoder, startParameter.getGradleHomeDir());
            nullableFileSerializer.write(encoder, startParameter.getProjectCacheDir());
            fileListSerializer.write(encoder, startParameter.getIncludedBuilds());
            encoder.writeBoolean(startParameter.isUseEmptySettingsWithoutDeprecationWarning());
            encoder.writeBoolean(startParameter.isSearchUpwardsWithoutDeprecationWarning());

            // Other stuff
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, startParameter.getProjectProperties());
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, startParameter.getSystemPropertiesArgs());
            fileListSerializer.write(encoder, startParameter.getInitScripts());
            stringListSerializer.write(encoder, startParameter.getLockedDependenciesToUpdate());

            // Flags
            encoder.writeBoolean(startParameter.isBuildProjectDependencies());
            encoder.writeBoolean(startParameter.isDryRun());
            encoder.writeBoolean(startParameter.isRerunTasks());
            encoder.writeBoolean(startParameter.isProfile());
            encoder.writeBoolean(startParameter.isContinueOnFailure());
            encoder.writeBoolean(startParameter.isOffline());
            encoder.writeBoolean(startParameter.isRefreshDependencies());
            encoder.writeBoolean(startParameter.isBuildCacheEnabled());
            encoder.writeBoolean(startParameter.isBuildCacheDebugLogging());
            encoder.writeBoolean(startParameter.isWatchFileSystem());
            encoder.writeBoolean(startParameter.isWatchFileSystemDebugLogging());
            encoder.writeBoolean(startParameter.isWatchFileSystemUsingDeprecatedOption());
            encoder.writeBoolean(startParameter.isVfsVerboseLogging());
            encoder.writeBoolean(startParameter.isConfigurationCache());
            encoder.writeString(startParameter.getConfigurationCacheProblems().name());
            encoder.writeSmallInt(startParameter.getConfigurationCacheMaxProblems());
            encoder.writeBoolean(startParameter.isConfigurationCacheRecreateCache());
            encoder.writeBoolean(startParameter.isConfigurationCacheQuiet());
            encoder.writeBoolean(startParameter.isConfigureOnDemand());
            encoder.writeBoolean(startParameter.isContinuous());
            encoder.writeBoolean(startParameter.isBuildScan());
            encoder.writeBoolean(startParameter.isNoBuildScan());
            encoder.writeBoolean(startParameter.isWriteDependencyLocks());
            stringListSerializer.write(encoder, startParameter.getWriteDependencyVerifications());
            encoder.writeString(startParameter.getDependencyVerificationMode().name());
            encoder.writeBoolean(startParameter.isRefreshKeys());
            encoder.writeBoolean(startParameter.isExportKeys());
        }

        private void writeTaskRequests(Encoder encoder, List<TaskExecutionRequest> taskRequests) throws Exception {
            encoder.writeSmallInt(taskRequests.size());
            for (TaskExecutionRequest taskRequest : taskRequests) {
                if (!(taskRequest instanceof DefaultTaskExecutionRequest)) {
                    // Only handle the command line for now
                    throw new UnsupportedOperationException();
                }
                DefaultTaskExecutionRequest request = (DefaultTaskExecutionRequest) taskRequests.get(0);
                stringListSerializer.write(encoder, request.getArgs());
            }
        }

        @Override
        public ExecuteBuildAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = new StartParameterInternal();

            // Logging configuration
            startParameter.setLogLevel(logLevelSerializer.read(decoder));
            startParameter.setShowStacktrace(showStacktraceSerializer.read(decoder));
            startParameter.setConsoleOutput(consoleOutputSerializer.read(decoder));
            startParameter.setWarningMode(warningModeSerializer.read(decoder));

            // Parallel configuration
            startParameter.setParallelProjectExecutionEnabled(decoder.readBoolean());
            startParameter.setMaxWorkerCount(decoder.readSmallInt());

            // Tasks
            startParameter.setTaskRequests(readTaskRequests(decoder));
            startParameter.setExcludedTaskNames(stringSetSerializer.read(decoder));

            // Layout
            startParameter.setBuildFile(nullableFileSerializer.read(decoder));
            startParameter.setProjectDir(nullableFileSerializer.read(decoder));
            startParameter.setSettingsFile(nullableFileSerializer.read(decoder));
            startParameter.setCurrentDir(FILE_SERIALIZER.read(decoder));
            startParameter.setGradleUserHomeDir(FILE_SERIALIZER.read(decoder));
            startParameter.setGradleHomeDir(nullableFileSerializer.read(decoder));
            startParameter.setProjectCacheDir(nullableFileSerializer.read(decoder));
            startParameter.setIncludedBuilds(fileListSerializer.read(decoder));
            if (decoder.readBoolean()) {
                startParameter.useEmptySettingsWithoutDeprecationWarning();
            }
            startParameter.setSearchUpwardsWithoutDeprecationWarning(decoder.readBoolean());

            // Other stuff
            startParameter.setProjectProperties(NO_NULL_STRING_MAP_SERIALIZER.read(decoder));
            startParameter.setSystemPropertiesArgs(NO_NULL_STRING_MAP_SERIALIZER.read(decoder));
            startParameter.setInitScripts(fileListSerializer.read(decoder));
            startParameter.setLockedDependenciesToUpdate(stringListSerializer.read(decoder));

            // Flags
            startParameter.setBuildProjectDependencies(decoder.readBoolean());
            startParameter.setDryRun(decoder.readBoolean());
            startParameter.setRerunTasks(decoder.readBoolean());
            startParameter.setProfile(decoder.readBoolean());
            startParameter.setContinueOnFailure(decoder.readBoolean());
            startParameter.setOffline(decoder.readBoolean());
            startParameter.setRefreshDependencies(decoder.readBoolean());
            startParameter.setBuildCacheEnabled(decoder.readBoolean());
            startParameter.setBuildCacheDebugLogging(decoder.readBoolean());
            startParameter.setWatchFileSystem(decoder.readBoolean());
            startParameter.setWatchFileSystemDebugLogging(decoder.readBoolean());
            startParameter.setWatchFileSystemUsingDeprecatedOption(decoder.readBoolean());
            startParameter.setVfsVerboseLogging(decoder.readBoolean());
            startParameter.setConfigurationCache(decoder.readBoolean());
            startParameter.setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value.valueOf(decoder.readString()));
            startParameter.setConfigurationCacheMaxProblems(decoder.readSmallInt());
            startParameter.setConfigurationCacheRecreateCache(decoder.readBoolean());
            startParameter.setConfigurationCacheQuiet(decoder.readBoolean());
            startParameter.setConfigureOnDemand(decoder.readBoolean());
            startParameter.setContinuous(decoder.readBoolean());
            startParameter.setBuildScan(decoder.readBoolean());
            startParameter.setNoBuildScan(decoder.readBoolean());
            startParameter.setWriteDependencyLocks(decoder.readBoolean());
            List<String> checksums = stringListSerializer.read(decoder);
            if (!checksums.isEmpty()) {
                startParameter.setWriteDependencyVerifications(checksums);
            }
            startParameter.setDependencyVerificationMode(DependencyVerificationMode.valueOf(decoder.readString()));
            startParameter.setRefreshKeys(decoder.readBoolean());
            startParameter.setExportKeys(decoder.readBoolean());

            return new ExecuteBuildAction(startParameter);
        }

        private List<TaskExecutionRequest> readTaskRequests(Decoder decoder) throws Exception {
            int requestCount = decoder.readSmallInt();
            List<TaskExecutionRequest> taskExecutionRequests = new ArrayList<>(requestCount);
            for (int i = 0; i < requestCount; i++) {
                taskExecutionRequests.add(new DefaultTaskExecutionRequest(stringListSerializer.read(decoder)));
            }
            return taskExecutionRequests;
        }
    }

    private static class NullableFileSerializer implements Serializer<File> {
        @Override
        public void write(Encoder encoder, File value) throws Exception {
            if (value == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeString(value.getPath());
            }
        }

        @Override
        public File read(Decoder decoder) throws Exception {
            if (decoder.readBoolean()) {
                return new File(decoder.readString());
            }
            return null;
        }
    }
}
