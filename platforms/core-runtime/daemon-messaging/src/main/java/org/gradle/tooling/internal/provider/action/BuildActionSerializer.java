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
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.invocation.parameters.ConfigurationCacheProblemsMode;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.RunDefaultTasksExecutionRequest;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.parameters.BuildParameters;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.internal.watch.registry.WatchMode;
import org.jspecify.annotations.Nullable;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.DefaultTaskSpec;
import org.gradle.tooling.internal.consumer.DefaultTestSpec;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.protocol.test.InternalTaskSpec;
import org.gradle.tooling.internal.protocol.test.InternalTestSpec;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.SerializedPayloadSerializer;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        registry.register(InternalTaskSpec.class, new InternalTaskSpecSerializer());
        return registry.build(BuildAction.class);
    }

    private static class BuildParametersSerializer implements Serializer<BuildParameters> {
        private static final byte NULLABLE_BOOLEAN_NULL = (byte) 0;
        private static final byte NULLABLE_BOOLEAN_FALSE = (byte) 1;
        private static final byte NULLABLE_BOOLEAN_TRUE = (byte) 2;

        private final BaseSerializerFactory serializerFactory = new BaseSerializerFactory();
        private final Serializer<LogLevel> logLevelSerializer = serializerFactory.getSerializerFor(LogLevel.class);
        private final Serializer<ShowStacktrace> showStacktraceSerializer = serializerFactory.getSerializerFor(ShowStacktrace.class);
        private final Serializer<ConsoleOutput> consoleOutputSerializer = serializerFactory.getSerializerFor(ConsoleOutput.class);
        private final Serializer<ConsoleUnicodeSupport> consoleUnicodeSupportSerializer = serializerFactory.getSerializerFor(ConsoleUnicodeSupport.class);
        private final Serializer<WarningMode> warningModeSerializer = serializerFactory.getSerializerFor(WarningMode.class);
        private final Serializer<File> nullableFileSerializer = new NullableFileSerializer();
        private final Serializer<List<String>> stringListSerializer = new ListSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);

        @Override
        public void write(Encoder encoder, BuildParameters params) throws Exception {
            // From DefaultLoggingConfiguration (non-null)
            logLevelSerializer.write(encoder, params.getLogLevel());
            showStacktraceSerializer.write(encoder, params.getShowStacktrace());
            consoleOutputSerializer.write(encoder, params.getConsoleOutput());
            consoleUnicodeSupportSerializer.write(encoder, params.getConsoleUnicodeSupport());
            warningModeSerializer.write(encoder, params.getWarningMode());
            encoder.writeBoolean(params.isNonInteractive());

            // From DefaultParallelismConfiguration (non-null)
            encoder.writeBoolean(params.isParallelProjectExecutionEnabled());
            encoder.writeSmallInt(params.getMaxWorkerCount());

            // From WelcomeMessageConfiguration (non-null)
            encoder.writeString(params.getWelcomeMessageDisplayMode().name());

            // TAPI override (nullable)
            writeNullableEnum(encoder, params.getLogLevelOverride());

            // Tasks
            writeTaskRequests(encoder, params.getTaskRequests());

            // Layout
            nullableFileSerializer.write(encoder, params.getProjectDir());
            FILE_SERIALIZER.write(encoder, params.getCurrentDir());
            FILE_SERIALIZER.write(encoder, params.getGradleUserHomeDir());
            nullableFileSerializer.write(encoder, params.getGradleHomeDir());

            // Always-present collections
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, params.getProjectProperties());
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, params.getSystemPropertiesArgs());

            // From ParsedOptions (all nullable)
            encoder.writeNullableString(params.getProjectCacheDir());
            writeNullableStringList(encoder, params.getInitScripts());
            writeNullableStringList(encoder, params.getExcludedTaskNames());
            writeNullableStringList(encoder, params.getIncludedBuilds());
            writeNullableBoolean(encoder, params.getBuildProjectDependencies());
            writeNullableBoolean(encoder, params.getDryRun());
            writeNullableBoolean(encoder, params.getRerunTasks());
            writeNullableBoolean(encoder, params.getProfile());
            writeNullableBoolean(encoder, params.getContinueOnFailure());
            writeNullableBoolean(encoder, params.getOffline());
            writeNullableBoolean(encoder, params.getRefreshDependencies());
            writeNullableBoolean(encoder, params.getBuildCacheEnabled());
            writeNullableBoolean(encoder, params.getBuildCacheDebugLogging());
            writeNullableEnum(encoder, params.getWatchFileSystemMode());
            writeNullableBoolean(encoder, params.getVfsVerboseLogging());
            writeNullableBoolean(encoder, params.getConfigurationCache());
            writeNullableBoolean(encoder, params.getIsolatedProjects());
            writeNullableEnum(encoder, params.getConfigurationCacheProblems());
            writeNullableBoolean(encoder, params.getConfigurationCacheIgnoreInputsDuringStore());
            writeNullableBoolean(encoder, params.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners());
            encoder.writeNullableSmallInt(params.getConfigurationCacheMaxProblems());
            encoder.writeNullableString(params.getConfigurationCacheIgnoredFileSystemCheckInputs());
            writeNullableBoolean(encoder, params.getConfigurationCacheDebug());
            writeNullableBoolean(encoder, params.getConfigurationCacheRecreateCache());
            writeNullableBoolean(encoder, params.getConfigurationCacheParallel());
            writeNullableBoolean(encoder, params.getConfigurationCacheReadOnly());
            writeNullableBoolean(encoder, params.getConfigurationCacheQuiet());
            writeNullableBoolean(encoder, params.getConfigurationCacheIntegrityCheckEnabled());
            encoder.writeNullableSmallInt(params.getConfigurationCacheEntriesPerKey());
            encoder.writeNullableString(params.getConfigurationCacheHeapDumpDir());
            writeNullableBoolean(encoder, params.getConfigurationCacheFineGrainedPropertyTracking());
            writeNullableBoolean(encoder, params.getConfigureOnDemand());
            writeNullableBoolean(encoder, params.getContinuous());
            writeNullableLong(encoder, params.getContinuousBuildQuietPeriod() != null ? params.getContinuousBuildQuietPeriod().toMillis() : null);
            writeNullableBoolean(encoder, params.getBuildScan());
            writeNullableBoolean(encoder, params.getWriteDependencyLocks());
            writeNullableStringList(encoder, params.getWriteDependencyVerifications());
            writeNullableEnum(encoder, params.getDependencyVerificationMode());
            writeNullableStringList(encoder, params.getLockedDependenciesToUpdate());
            writeNullableBoolean(encoder, params.getRefreshKeys());
            writeNullableBoolean(encoder, params.getExportKeys());
            writeNullableBoolean(encoder, params.getPropertyUpgradeReportEnabled());
            writeNullableBoolean(encoder, params.getProblemReportGenerationEnabled());
            writeNullableBoolean(encoder, params.getTaskGraph());
            writeNullableBoolean(encoder, params.getParallelToolingModelBuilding());
            encoder.writeNullableString(params.getDevelocityUrl());
            encoder.writeNullableString(params.getDevelocityPluginVersion());
        }

        @Override
        public BuildParameters read(Decoder decoder) throws Exception {
            return new BuildParameters(
                // From DefaultLoggingConfiguration (non-null)
                logLevelSerializer.read(decoder),
                showStacktraceSerializer.read(decoder),
                consoleOutputSerializer.read(decoder),
                consoleUnicodeSupportSerializer.read(decoder),
                warningModeSerializer.read(decoder),
                decoder.readBoolean(),
                // From DefaultParallelismConfiguration (non-null)
                decoder.readBoolean(),
                decoder.readSmallInt(),
                // From WelcomeMessageConfiguration (non-null)
                WelcomeMessageDisplayMode.valueOf(decoder.readString()),
                // TAPI override (nullable)
                readNullableEnum(decoder, LogLevel.class),
                // Tasks
                readTaskRequests(decoder),
                // Layout
                nullableFileSerializer.read(decoder),
                FILE_SERIALIZER.read(decoder),
                FILE_SERIALIZER.read(decoder),
                nullableFileSerializer.read(decoder),
                // Always-present collections
                NO_NULL_STRING_MAP_SERIALIZER.read(decoder),
                NO_NULL_STRING_MAP_SERIALIZER.read(decoder),
                // From ParsedOptions (all nullable)
                decoder.readNullableString(),
                readNullableStringList(decoder),
                readNullableStringList(decoder),
                readNullableStringList(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableEnum(decoder, WatchMode.class),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableEnum(decoder, ConfigurationCacheProblemsMode.class),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                decoder.readNullableSmallInt(),
                decoder.readNullableString(),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                decoder.readNullableSmallInt(),
                decoder.readNullableString(),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableDuration(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableStringList(decoder),
                readNullableEnum(decoder, DependencyVerificationMode.class),
                readNullableStringList(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                readNullableBoolean(decoder),
                decoder.readNullableString(),
                decoder.readNullableString()
            );
        }

        // Nullable boolean: 0=null, 1=false, 2=true
        private static void writeNullableBoolean(Encoder encoder, @Nullable Boolean value) throws IOException {
            if (value == null) {
                encoder.writeByte(NULLABLE_BOOLEAN_NULL);
            } else if (value) {
                encoder.writeByte(NULLABLE_BOOLEAN_TRUE);
            } else {
                encoder.writeByte(NULLABLE_BOOLEAN_FALSE);
            }
        }

        private static @Nullable Boolean readNullableBoolean(Decoder decoder) throws IOException {
            byte b = decoder.readByte();
            if (b == NULLABLE_BOOLEAN_NULL) {
                return null;
            } else if (b == NULLABLE_BOOLEAN_TRUE) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        }

        private static <E extends Enum<E>> void writeNullableEnum(Encoder encoder, @Nullable Enum<E> value) throws IOException {
            encoder.writeNullableString(value != null ? value.name() : null);
        }

        private static <E extends Enum<E>> @Nullable E readNullableEnum(Decoder decoder, Class<E> enumType) throws IOException {
            String name = decoder.readNullableString();
            return name != null ? Enum.valueOf(enumType, name) : null;
        }

        private void writeNullableStringList(Encoder encoder, @Nullable List<String> value) throws Exception {
            if (value == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                stringListSerializer.write(encoder, value);
            }
        }

        private @Nullable List<String> readNullableStringList(Decoder decoder) throws Exception {
            if (decoder.readBoolean()) {
                return stringListSerializer.read(decoder);
            }
            return null;
        }

        private static void writeNullableLong(Encoder encoder, @Nullable Long value) throws IOException {
            if (value == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeLong(value);
            }
        }

        private static @Nullable Duration readNullableDuration(Decoder decoder) throws IOException {
            if (decoder.readBoolean()) {
                return Duration.ofMillis(decoder.readLong());
            }
            return null;
        }

        private void writeTaskRequests(Encoder encoder, List<TaskExecutionRequest> taskRequests) throws Exception {
            encoder.writeSmallInt(taskRequests.size());
            for (TaskExecutionRequest taskRequest : taskRequests) {
                if (taskRequest instanceof RunDefaultTasksExecutionRequest) {
                    encoder.writeByte((byte) 0);
                } else if (taskRequest instanceof DefaultTaskExecutionRequest) {
                    DefaultTaskExecutionRequest request = (DefaultTaskExecutionRequest) taskRequest;
                    encoder.writeByte((byte) 1);
                    encoder.writeNullableString(request.getProjectPath());
                    nullableFileSerializer.write(encoder, request.getRootDir());
                    stringListSerializer.write(encoder, request.getArgs());
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        }

        private List<TaskExecutionRequest> readTaskRequests(Decoder decoder) throws Exception {
            int requestCount = decoder.readSmallInt();
            List<TaskExecutionRequest> taskExecutionRequests = new ArrayList<>(requestCount);
            for (int i = 0; i < requestCount; i++) {
                byte tag = decoder.readByte();
                if (tag == 0) {
                    taskExecutionRequests.add(new RunDefaultTasksExecutionRequest());
                } else if (tag == 1) {
                    String projectPath = decoder.readNullableString();
                    File rootDir = nullableFileSerializer.read(decoder);
                    List<String> args = stringListSerializer.read(decoder);
                    taskExecutionRequests.add(new DefaultTaskExecutionRequest(args, projectPath, rootDir));
                } else {
                    throw new IllegalStateException();
                }
            }
            return taskExecutionRequests;
        }
    }

    private static class ExecuteBuildActionSerializer implements Serializer<ExecuteBuildAction> {
        private final Serializer<BuildParameters> buildParametersSerializer = new BuildParametersSerializer();

        @Override
        public void write(Encoder encoder, ExecuteBuildAction action) throws Exception {
            buildParametersSerializer.write(encoder, action.getBuildParameters());
        }

        @Override
        public ExecuteBuildAction read(Decoder decoder) throws Exception {
            BuildParameters buildParameters = buildParametersSerializer.read(decoder);
            return new ExecuteBuildAction(buildParameters);
        }
    }

    private static class BuildModelActionSerializer implements Serializer<BuildModelAction> {
        private final Serializer<BuildParameters> buildParametersSerializer = new BuildParametersSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, BuildModelAction value) throws Exception {
            buildParametersSerializer.write(encoder, value.getBuildParameters());
            encoder.writeString(value.getModelName());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public BuildModelAction read(Decoder decoder) throws Exception {
            BuildParameters buildParameters = buildParametersSerializer.read(decoder);
            String modelName = decoder.readString();
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new BuildModelAction(buildParameters, modelName, runTasks, buildEventSubscriptions);
        }
    }

    private static class ClientProvidedBuildActionSerializer implements Serializer<ClientProvidedBuildAction> {
        private final Serializer<BuildParameters> buildParametersSerializer = new BuildParametersSerializer();
        private final Serializer<SerializedPayload> payloadSerializer = new SerializedPayloadSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, ClientProvidedBuildAction value) throws Exception {
            buildParametersSerializer.write(encoder, value.getBuildParameters());
            payloadSerializer.write(encoder, value.getAction());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public ClientProvidedBuildAction read(Decoder decoder) throws Exception {
            BuildParameters buildParameters = buildParametersSerializer.read(decoder);
            SerializedPayload action = payloadSerializer.read(decoder);
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new ClientProvidedBuildAction(buildParameters, action, runTasks, buildEventSubscriptions);
        }
    }

    private static class ClientProvidedPhasedActionSerializer implements Serializer<ClientProvidedPhasedAction> {
        private final Serializer<BuildParameters> buildParametersSerializer = new BuildParametersSerializer();
        private final Serializer<SerializedPayload> payloadSerializer = new SerializedPayloadSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();

        @Override
        public void write(Encoder encoder, ClientProvidedPhasedAction value) throws Exception {
            buildParametersSerializer.write(encoder, value.getBuildParameters());
            payloadSerializer.write(encoder, value.getPhasedAction());
            encoder.writeBoolean(value.isRunTasks());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
        }

        @Override
        public ClientProvidedPhasedAction read(Decoder decoder) throws Exception {
            BuildParameters buildParameters = buildParametersSerializer.read(decoder);
            SerializedPayload action = payloadSerializer.read(decoder);
            boolean runTasks = decoder.readBoolean();
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            return new ClientProvidedPhasedAction(buildParameters, action, runTasks, buildEventSubscriptions);
        }
    }

    private static class TestExecutionRequestPayload implements Serializable {
        final Set<InternalTestDescriptor> testDescriptors;
        final Set<String> classNames;
        final Set<InternalJvmTestRequest> internalJvmTestRequests;
        final InternalDebugOptions debugOptions;
        final Map<String, List<InternalJvmTestRequest>> taskAndTests;
        final boolean isRunDefaultTasks;

        public TestExecutionRequestPayload(Set<InternalTestDescriptor> testDescriptors, Set<String> classNames, Set<InternalJvmTestRequest> internalJvmTestRequests, InternalDebugOptions debugOptions, Map<String, List<InternalJvmTestRequest>> taskAndTests, boolean isRunDefaultTasks) {
            this.testDescriptors = testDescriptors;
            this.classNames = classNames;
            this.internalJvmTestRequests = internalJvmTestRequests;
            this.debugOptions = debugOptions;
            this.taskAndTests = taskAndTests;
            this.isRunDefaultTasks = isRunDefaultTasks;
        }
    }

    private static class TestExecutionRequestActionSerializer implements Serializer<TestExecutionRequestAction> {
        private final Serializer<BuildParameters> buildParametersSerializer = new BuildParametersSerializer();
        private final Serializer<BuildEventSubscriptions> buildEventSubscriptionsSerializer = new BuildEventSubscriptionsSerializer();
        private final Serializer<TestExecutionRequestPayload> payloadSerializer = new DefaultSerializer<>();
        private final Serializer<InternalTaskSpec> taskSpecSerializer = new InternalTaskSpecSerializer();

        @Override
        public void write(Encoder encoder, TestExecutionRequestAction value) throws Exception {
            buildParametersSerializer.write(encoder, value.getBuildParameters());
            buildEventSubscriptionsSerializer.write(encoder, value.getClientSubscriptions());
            payloadSerializer.write(encoder, new TestExecutionRequestPayload(
                value.getTestExecutionDescriptors(),
                value.getTestClassNames(),
                value.getInternalJvmTestRequests(),
                value.getDebugOptions(),
                value.getTaskAndTests(),
                value.isRunDefaultTasks()
            ));

            encoder.writeSmallInt(value.getTaskSpecs().size());
            for (InternalTaskSpec taskSpec : value.getTaskSpecs()) {
                taskSpecSerializer.write(encoder, taskSpec);
            }
        }

        @Override
        public TestExecutionRequestAction read(Decoder decoder) throws Exception {
            BuildParameters buildParameters = buildParametersSerializer.read(decoder);
            BuildEventSubscriptions buildEventSubscriptions = buildEventSubscriptionsSerializer.read(decoder);
            TestExecutionRequestPayload payload = payloadSerializer.read(decoder);
            int numOfPatterns = decoder.readSmallInt();
            List<InternalTaskSpec> taskSpecs = new ArrayList<>(numOfPatterns);
            for (int i = 0; i < numOfPatterns; i++) {
                taskSpecs.add(i, taskSpecSerializer.read(decoder));
            }
            return new TestExecutionRequestAction(buildEventSubscriptions, buildParameters, payload.testDescriptors, payload.classNames, payload.internalJvmTestRequests, payload.debugOptions, payload.taskAndTests, payload.isRunDefaultTasks, taskSpecs);
        }
    }

    private static class InternalTaskSpecSerializer implements Serializer<InternalTaskSpec> {

        private final Serializer<List<String>> stringListSerializer = new ListSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);

        @Override
        public void write(Encoder encoder, InternalTaskSpec value) throws Exception {
            if (value instanceof InternalTestSpec) {
                encoder.writeSmallInt(0);
                InternalTestSpec test = (InternalTestSpec) value;
                encoder.writeString(value.getTaskPath());
                stringListSerializer.write(encoder, test.getClasses());
                stringListSerializer.write(encoder, test.getPatterns());
                stringListSerializer.write(encoder, test.getPackages());
                Map<String, List<String>> methods = test.getMethods();
                encoder.writeSmallInt(methods.size());
                for (Map.Entry<String, List<String>> entry : methods.entrySet()) {
                    String cls = entry.getKey();
                    List<String> method = entry.getValue();
                    encoder.writeString(cls);
                    stringListSerializer.write(encoder, method);
                }
            } else {
                encoder.writeSmallInt(1);
                encoder.writeString(value.getTaskPath());
            }
        }

        @Override
        public InternalTaskSpec read(Decoder decoder) throws Exception {
            int type = decoder.readSmallInt();
            if (type == 0) {
                String taskPath = decoder.readString();
                List<String> classes = stringListSerializer.read(decoder);
                List<String> patterns = stringListSerializer.read(decoder);
                List<String> packages = stringListSerializer.read(decoder);
                int methodsSize = decoder.readSmallInt();
                Map<String, List<String>> methods = new LinkedHashMap<>();
                for (int i = 0; i < methodsSize; i++) {
                    String cls = decoder.readString();
                    List<String> method = stringListSerializer.read(decoder);
                    methods.put(cls, method);
                }
                return new DefaultTestSpec(taskPath, classes, methods, packages, patterns);
            } else {
                String taskPath = decoder.readString();
                return new DefaultTaskSpec(taskPath);
            }
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
