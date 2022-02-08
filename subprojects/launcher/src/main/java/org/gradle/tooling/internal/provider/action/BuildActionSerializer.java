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

package org.gradle.tooling.internal.provider.action;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.SerializedPayloadSerializer;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER;

public class BuildActionSerializer {
    public static Serializer<BuildAction> create() {
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();
        registry.register(ExecuteBuildAction.class, new ExecuteBuildActionSerializer());
        registry.register(BuildModelAction.class, new BuildModelActionSerializer());
        registry.register(ClientProvidedBuildAction.class, new ClientProvidedBuildActionSerializer());
        registry.register(ClientProvidedPhasedAction.class, new ClientProvidedPhasedActionSerializer());
        registry.register(TestExecutionRequestAction.class, new TestExecutionRequestActionSerializer());
        return registry.build(BuildAction.class);
    }

    private static class StartParameterSerializer implements Serializer<StartParameterInternal> {
        private final Serializer<LogLevel> logLevelSerializer;
        private final Serializer<ShowStacktrace> showStacktraceSerializer;
        private final Serializer<ConsoleOutput> consoleOutputSerializer;
        private final Serializer<WarningMode> warningModeSerializer;
        private final Serializer<File> nullableFileSerializer = new NullableFileSerializer();
        private final Serializer<List<String>> stringListSerializer = new ListSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);
        private final Serializer<List<File>> fileListSerializer = new ListSerializer<>(BaseSerializerFactory.FILE_SERIALIZER);
        private final Serializer<Set<String>> stringSetSerializer = new SetSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);
        private final Serializer<BuildOption.Value<Boolean>> valueSerializer = new ValueSerializer();

        StartParameterSerializer() {
            BaseSerializerFactory serializerFactory = new BaseSerializerFactory();
            logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class);
            showStacktraceSerializer = serializerFactory.getSerializerFor(ShowStacktrace.class);
            consoleOutputSerializer = serializerFactory.getSerializerFor(ConsoleOutput.class);
            warningModeSerializer = serializerFactory.getSerializerFor(WarningMode.class);
        }

        @Override
        public void write(Encoder encoder, StartParameterInternal startParameter) throws Exception {
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
            @SuppressWarnings("deprecation")
            File customBuildFile = startParameter.getBuildFile();
            nullableFileSerializer.write(encoder, customBuildFile);
            nullableFileSerializer.write(encoder, startParameter.getProjectDir());
            @SuppressWarnings("deprecation")
            File customSettingsFile = startParameter.getSettingsFile();
            nullableFileSerializer.write(encoder, customSettingsFile);
            FILE_SERIALIZER.write(encoder, startParameter.getCurrentDir());
            FILE_SERIALIZER.write(encoder, startParameter.getGradleUserHomeDir());
            nullableFileSerializer.write(encoder, startParameter.getGradleHomeDir());
            nullableFileSerializer.write(encoder, startParameter.getProjectCacheDir());
            fileListSerializer.write(encoder, startParameter.getIncludedBuilds());

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
            encoder.writeString(startParameter.getWatchFileSystemMode().name());
            encoder.writeBoolean(startParameter.isWatchFileSystemDebugLogging());
            encoder.writeBoolean(startParameter.isVfsVerboseLogging());
            valueSerializer.write(encoder, startParameter.getConfigurationCache());
            valueSerializer.write(encoder, startParameter.getIsolatedProjects());
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
                DefaultTaskExecutionRequest request = (DefaultTaskExecutionRequest) taskRequest;
                encoder.writeNullableString(request.getProjectPath());
                nullableFileSerializer.write(encoder, request.getRootDir());
                stringListSerializer.write(encoder, request.getArgs());
            }
        }

        @SuppressWarnings("deprecation") // StartParameter.setBuildFile and StartParameter.setSettingsFile
        @Override
        public StartParameterInternal read(Decoder decoder) throws Exception {
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
            startParameter.setWatchFileSystemMode(WatchMode.valueOf(decoder.readString()));
            startParameter.setWatchFileSystemDebugLogging(decoder.readBoolean());
            startParameter.setVfsVerboseLogging(decoder.readBoolean());
            startParameter.setConfigurationCache(valueSerializer.read(decoder));
            startParameter.setIsolatedProjects(valueSerializer.read(decoder));
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

            return startParameter;
        }

        private List<TaskExecutionRequest> readTaskRequests(Decoder decoder) throws Exception {
            int requestCount = decoder.readSmallInt();
            List<TaskExecutionRequest> taskExecutionRequests = new ArrayList<>(requestCount);
            for (int i = 0; i < requestCount; i++) {
                String projectPath = decoder.readNullableString();
                File rootDir = nullableFileSerializer.read(decoder);
                List<String> args = stringListSerializer.read(decoder);
                taskExecutionRequests.add(new DefaultTaskExecutionRequest(args, projectPath, rootDir));
            }
            return taskExecutionRequests;
        }
    }

    private static class ExecuteBuildActionSerializer implements Serializer<ExecuteBuildAction> {
        private final Serializer<StartParameterInternal> startParameterSerializer = new StartParameterSerializer();

        @Override
        public void write(Encoder encoder, ExecuteBuildAction action) throws Exception {
            StartParameterInternal startParameter = action.getStartParameter();
            startParameterSerializer.write(encoder, startParameter);
        }

        @Override
        public ExecuteBuildAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = startParameterSerializer.read(decoder);
            return new ExecuteBuildAction(startParameter);
        }
    }

    private static class BuildModelActionSerializer implements Serializer<BuildModelAction> {
        private final Serializer<StartParameterInternal> startParameterSerializer = new StartParameterSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, BuildModelAction value) throws Exception {
            startParameterSerializer.write(encoder, value.getStartParameter());
            encoder.writeString(value.getModelName());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public BuildModelAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = startParameterSerializer.read(decoder);
            String modelName = decoder.readString();
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new BuildModelAction(startParameter, modelName, runTasks, buildEventSubscriptions);
        }
    }

    private static class ClientProvidedBuildActionSerializer implements Serializer<ClientProvidedBuildAction> {
        private final Serializer<StartParameterInternal> startParameterSerializer = new StartParameterSerializer();
        private final Serializer<SerializedPayload> payloadSerializer = new SerializedPayloadSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, ClientProvidedBuildAction value) throws Exception {
            startParameterSerializer.write(encoder, value.getStartParameter());
            payloadSerializer.write(encoder, value.getAction());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public ClientProvidedBuildAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = startParameterSerializer.read(decoder);
            SerializedPayload action = payloadSerializer.read(decoder);
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new ClientProvidedBuildAction(startParameter, action, runTasks, buildEventSubscriptions);
        }
    }

    private static class ClientProvidedPhasedActionSerializer implements Serializer<ClientProvidedPhasedAction> {
        private final Serializer<StartParameterInternal> startParameterSerializer = new StartParameterSerializer();
        private final Serializer<SerializedPayload> payloadSerializer = new SerializedPayloadSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, ClientProvidedPhasedAction value) throws Exception {
            startParameterSerializer.write(encoder, value.getStartParameter());
            payloadSerializer.write(encoder, value.getPhasedAction());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public ClientProvidedPhasedAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = startParameterSerializer.read(decoder);
            SerializedPayload action = payloadSerializer.read(decoder);
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new ClientProvidedPhasedAction(startParameter, action, runTasks, buildEventSubscriptions);
        }
    }

    private static class TestExecutionRequestPayload implements Serializable {
        final Set<InternalTestDescriptor> testDescriptors;
        final Set<String> classNames;
        final Set<InternalJvmTestRequest> internalJvmTestRequests;
        final InternalDebugOptions debugOptions;
        final Map<String, List<InternalJvmTestRequest>> taskAndTests;

        public TestExecutionRequestPayload(Set<InternalTestDescriptor> testDescriptors, Set<String> classNames, Set<InternalJvmTestRequest> internalJvmTestRequests, InternalDebugOptions debugOptions, Map<String, List<InternalJvmTestRequest>> taskAndTests) {
            this.testDescriptors = testDescriptors;
            this.classNames = classNames;
            this.internalJvmTestRequests = internalJvmTestRequests;
            this.debugOptions = debugOptions;
            this.taskAndTests = taskAndTests;
        }
    }

    private static class TestExecutionRequestActionSerializer implements Serializer<TestExecutionRequestAction> {
        private final Serializer<StartParameterInternal> startParameterSerializer = new StartParameterSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();
        private final Serializer<TestExecutionRequestPayload> payloadSerializer = new DefaultSerializer<>();

        @Override
        public void write(Encoder encoder, TestExecutionRequestAction value) throws Exception {
            startParameterSerializer.write(encoder, value.getStartParameter());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
            payloadSerializer.write(encoder, new TestExecutionRequestPayload(
                value.getTestExecutionDescriptors(),
                value.getTestClassNames(),
                value.getInternalJvmTestRequests(),
                value.getDebugOptions(),
                value.getTaskAndTests()
            ));
        }

        @Override
        public TestExecutionRequestAction read(Decoder decoder) throws Exception {
            StartParameterInternal startParameter = startParameterSerializer.read(decoder);
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            TestExecutionRequestPayload payload = payloadSerializer.read(decoder);
            return new TestExecutionRequestAction(buildEventSubscriptions, startParameter, payload.testDescriptors, payload.classNames, payload.internalJvmTestRequests, payload.debugOptions, payload.taskAndTests);
        }
    }

    private static class BuildEventSubscriptionsSerializer implements Serializer<BuildEventSubscriptions> {
        private final Serializer<Set<OperationType>> setSerializer;

        public BuildEventSubscriptionsSerializer() {
            BaseSerializerFactory serializerFactory = new BaseSerializerFactory();
            this.setSerializer = new SetSerializer<>(serializerFactory.getSerializerFor(OperationType.class));
        }

        @Override
        public void write(Encoder encoder, BuildEventSubscriptions value) throws Exception {
            setSerializer.write(encoder, value.getOperationTypes());
        }

        @Override
        public BuildEventSubscriptions read(Decoder decoder) throws Exception {
            return new BuildEventSubscriptions(setSerializer.read(decoder));
        }
    }

    private static class ValueSerializer implements Serializer<BuildOption.Value<Boolean>> {
        private static final byte EXPLICIT_TRUE = (byte) 1;
        private static final byte EXPLICIT_FALSE = (byte) 2;
        public static final byte IMPLICIT_TRUE = (byte) 3;
        public static final byte IMPLICIT_FALSE = (byte) 4;

        @Override
        public BuildOption.Value<Boolean> read(Decoder decoder) throws Exception {
            switch (decoder.readByte()) {
                case EXPLICIT_TRUE:
                    return BuildOption.Value.value(true);
                case EXPLICIT_FALSE:
                    return BuildOption.Value.value(false);
                case IMPLICIT_TRUE:
                    return BuildOption.Value.defaultValue(true);
                case IMPLICIT_FALSE:
                    return BuildOption.Value.defaultValue(false);
                default:
                    throw new IllegalStateException();
            }
        }

        @Override
        public void write(Encoder encoder, BuildOption.Value<Boolean> value) throws Exception {
            if (value.isExplicit() && value.get()) {
                encoder.writeByte(EXPLICIT_TRUE);
            } else if (value.isExplicit()) {
                encoder.writeByte(EXPLICIT_FALSE);
            } else if (value.get()) {
                encoder.writeByte(IMPLICIT_TRUE);
            } else {
                encoder.writeByte(IMPLICIT_FALSE);
            }
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
