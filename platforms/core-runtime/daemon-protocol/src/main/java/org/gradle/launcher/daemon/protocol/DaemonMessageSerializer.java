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

package org.gradle.launcher.daemon.protocol;

import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.events.BooleanQuestionPromptEvent;
import org.gradle.internal.logging.events.IntQuestionPromptEvent;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.ReadStdInEvent;
import org.gradle.internal.logging.events.SelectOptionPromptEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.events.TextQuestionPromptEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.events.YesNoQuestionPromptEvent;
import org.gradle.internal.logging.serializer.BooleanQuestionPromptEventSerializer;
import org.gradle.internal.logging.serializer.IntQuestionPromptEventSerializer;
import org.gradle.internal.logging.serializer.LogEventSerializer;
import org.gradle.internal.logging.serializer.LogLevelChangeEventSerializer;
import org.gradle.internal.logging.serializer.ProgressCompleteEventSerializer;
import org.gradle.internal.logging.serializer.ProgressEventSerializer;
import org.gradle.internal.logging.serializer.ProgressStartEventSerializer;
import org.gradle.internal.logging.serializer.ReadStdInEventSerializer;
import org.gradle.internal.logging.serializer.SelectOptionPromptEventSerializer;
import org.gradle.internal.logging.serializer.SpanSerializer;
import org.gradle.internal.logging.serializer.StyledTextOutputEventSerializer;
import org.gradle.internal.logging.serializer.TextQuestionPromptEventSerializer;
import org.gradle.internal.logging.serializer.UserInputRequestEventSerializer;
import org.gradle.internal.logging.serializer.UserInputResumeEventSerializer;
import org.gradle.internal.logging.serializer.YesNoQuestionPromptEventSerializer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.SerializedPayloadSerializer;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER;

public class DaemonMessageSerializer {
    public static Serializer<Message> create(Serializer<BuildAction> buildActionSerializer) {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        Serializer<LogLevel> logLevelSerializer = factory.getSerializerFor(LogLevel.class);
        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();

        // Lifecycle messages
        registry.register(Build.class, new BuildSerializer(buildActionSerializer));
        registry.register(Cancel.class, new CancelSerializer());
        registry.register(DaemonUnavailable.class, new DaemonUnavailableSerializer());
        registry.register(BuildStarted.class, new BuildStartedSerializer());
        registry.register(Failure.class, new FailureSerializer(throwableSerializer));
        registry.register(Success.class, new SuccessSerializer());
        registry.register(Finished.class, new FinishedSerializer());

        // Build events
        registry.register(BuildEvent.class, new BuildEventSerializer());

        // Input events
        registry.register(ForwardInput.class, new ForwardInputSerializer());
        registry.register(UserResponse.class, new UserResponseSerializer());
        registry.register(CloseInput.class, new CloseInputSerializer());

        // Output events
        registry.register(LogEvent.class, new LogEventSerializer(logLevelSerializer, throwableSerializer));
        registry.register(UserInputRequestEvent.class, new UserInputRequestEventSerializer());
        registry.register(YesNoQuestionPromptEvent.class, new YesNoQuestionPromptEventSerializer());
        registry.register(BooleanQuestionPromptEvent.class, new BooleanQuestionPromptEventSerializer());
        registry.register(TextQuestionPromptEvent.class, new TextQuestionPromptEventSerializer());
        registry.register(IntQuestionPromptEvent.class, new IntQuestionPromptEventSerializer());
        registry.register(SelectOptionPromptEvent.class, new SelectOptionPromptEventSerializer());
        registry.register(UserInputResumeEvent.class, new UserInputResumeEventSerializer());
        registry.register(ReadStdInEvent.class, new ReadStdInEventSerializer());
        registry.register(StyledTextOutputEvent.class, new StyledTextOutputEventSerializer(logLevelSerializer, new ListSerializer<>(new SpanSerializer(factory.getSerializerFor(StyledTextOutput.Style.class)))));
        registry.register(ProgressStartEvent.class, new ProgressStartEventSerializer());
        registry.register(ProgressCompleteEvent.class, new ProgressCompleteEventSerializer());
        registry.register(ProgressEvent.class, new ProgressEventSerializer());
        registry.register(LogLevelChangeEvent.class, new LogLevelChangeEventSerializer(logLevelSerializer));
        registry.register(OutputMessage.class, new OutputMessageSerializer(registry.build(OutputEvent.class)));

        // Default for everything else
        registry.useJavaSerialization(Message.class);

        return registry.build(Message.class);
    }

    private static class SuccessSerializer implements Serializer<Success> {
        private final Serializer<Object> javaSerializer = new DefaultSerializer<>();
        private final Serializer<SerializedPayload> payloadSerializer = new SerializedPayloadSerializer();

        @Override
        public void write(Encoder encoder, Success success) throws Exception {
            if (success.getValue() == null) {
                encoder.writeByte((byte) 0);
            } else if (success.getValue() instanceof BuildActionResult) {
                BuildActionResult result = (BuildActionResult) success.getValue();
                if (result.getResult() != null) {
                    if (result.getException() != null || result.getFailure() != null || result.wasCancelled()) {
                        throw new IllegalArgumentException("Result should not have both a result object and a failure associated with it.");
                    }
                    if (result.getResult().getHeader() == null && result.getResult().getSerializedModel().isEmpty()) {
                        // Special case "build successful" when there is no result object to send
                        encoder.writeByte((byte) 1);
                    } else {
                        encoder.writeByte((byte) 2);
                        payloadSerializer.write(encoder, result.getResult());
                    }
                } else if (result.getFailure() != null) {
                    encoder.writeByte((byte) 3);
                    encoder.writeBoolean(result.wasCancelled());
                    payloadSerializer.write(encoder, result.getFailure());
                } else {
                    encoder.writeByte((byte) 4);
                    encoder.writeBoolean(result.wasCancelled());
                    javaSerializer.write(encoder, result.getException());
                }
            } else {
                // Serialize anything else
                encoder.writeByte((byte) 5);
                javaSerializer.write(encoder, success.getValue());
            }
        }

        @Override
        public Success read(Decoder decoder) throws Exception {
            boolean wasCancelled;
            byte tag = decoder.readByte();
            switch (tag) {
                case 0:
                    return new Success(null);
                case 1:
                    return new Success(BuildActionResult.of(new SerializedPayload(null, Collections.emptyList())));
                case 2:
                    SerializedPayload result = payloadSerializer.read(decoder);
                    return new Success(BuildActionResult.of(result));
                case 3:
                    wasCancelled = decoder.readBoolean();
                    SerializedPayload failure = payloadSerializer.read(decoder);
                    return new Success(BuildActionResult.failed(wasCancelled, failure, null));
                case 4:
                    wasCancelled = decoder.readBoolean();
                    RuntimeException exception = (RuntimeException) javaSerializer.read(decoder);
                    return new Success(BuildActionResult.failed(wasCancelled, null, exception));
                case 5:
                    return new Success(javaSerializer.read(decoder));
                default:
                    throw new IllegalArgumentException("Unexpected payload type.");
            }
        }
    }

    private static class FailureSerializer implements Serializer<Failure> {
        private final Serializer<Throwable> throwableSerializer;

        FailureSerializer(Serializer<Throwable> throwableSerializer) {
            this.throwableSerializer = throwableSerializer;
        }

        @Override
        public void write(Encoder encoder, Failure failure) throws Exception {
            throwableSerializer.write(encoder, failure.getValue());
        }

        @Override
        public Failure read(Decoder decoder) throws Exception {
            return new Failure(throwableSerializer.read(decoder));
        }
    }

    private static class BuildEventSerializer implements Serializer<BuildEvent> {
        private final Serializer<Object> payloadSerializer = new DefaultSerializer<>();

        @Override
        public void write(Encoder encoder, BuildEvent buildEvent) throws Exception {
            payloadSerializer.write(encoder, buildEvent.getPayload());
        }

        @Override
        public BuildEvent read(Decoder decoder) throws Exception {
            return new BuildEvent(payloadSerializer.read(decoder));
        }
    }

    private static class ForwardInputSerializer implements Serializer<ForwardInput> {
        @Override
        public void write(Encoder encoder, ForwardInput message) throws Exception {
            encoder.writeBinary(message.getBytes());
        }

        @Override
        public ForwardInput read(Decoder decoder) throws Exception {
            return new ForwardInput(decoder.readBinary());
        }
    }

    private static class UserResponseSerializer implements Serializer<UserResponse> {
        @Override
        public void write(Encoder encoder, UserResponse message) throws Exception {
            encoder.writeString(message.getResponse());
        }

        @Override
        public UserResponse read(Decoder decoder) throws Exception {
            return new UserResponse(decoder.readString());
        }
    }

    private static class CloseInputSerializer implements Serializer<CloseInput> {
        @Override
        public void write(Encoder encoder, CloseInput value) {
        }

        @Override
        public CloseInput read(Decoder decoder) {
            return new CloseInput();
        }
    }

    private static class OutputMessageSerializer implements Serializer<OutputMessage> {
        private final Serializer<OutputEvent> eventSerializer;

        OutputMessageSerializer(Serializer<OutputEvent> eventSerializer) {
            this.eventSerializer = eventSerializer;
        }

        @Override
        public void write(Encoder encoder, OutputMessage message) throws Exception {
            eventSerializer.write(encoder, message.getEvent());
        }

        @Override
        public OutputMessage read(Decoder decoder) throws Exception {
            return new OutputMessage(eventSerializer.read(decoder));
        }
    }

    private static class BuildSerializer implements Serializer<Build> {
        private final Serializer<BuildAction> buildActionSerializer;
        private final Serializer<BuildActionParameters> buildActionParametersSerializer = new BuildActionParametersSerializer();

        private BuildSerializer(Serializer<BuildAction> buildActionSerializer) {
            this.buildActionSerializer = buildActionSerializer;
        }

        @Override
        public void write(Encoder encoder, Build build) throws Exception {
            encoder.writeLong(build.getIdentifier().getMostSignificantBits());
            encoder.writeLong(build.getIdentifier().getLeastSignificantBits());
            encoder.writeBinary(build.getToken());
            encoder.writeLong(build.getStartTime());
            encoder.writeBoolean(build.isInteractive());
            buildActionSerializer.write(encoder, build.getAction());
            GradleLauncherMetaData metaData = build.getBuildClientMetaData();
            encoder.writeString(metaData.getAppName());
            buildActionParametersSerializer.write(encoder, build.getParameters());
        }

        @Override
        public Build read(Decoder decoder) throws Exception {
            UUID uuid = new UUID(decoder.readLong(), decoder.readLong());
            byte[] token = decoder.readBinary();
            long timestamp = decoder.readLong();
            boolean interactive = decoder.readBoolean();
            BuildAction buildAction = buildActionSerializer.read(decoder);
            GradleLauncherMetaData metaData = new GradleLauncherMetaData(decoder.readString());
            BuildActionParameters buildActionParameters = buildActionParametersSerializer.read(decoder);
            return new Build(uuid, token, buildAction, metaData, timestamp, interactive, buildActionParameters);
        }
    }

    private static class BuildActionParametersSerializer implements Serializer<BuildActionParameters> {
        private final Serializer<LogLevel> logLevelSerializer;
        private final Serializer<List<File>> classPathSerializer;

        BuildActionParametersSerializer() {
            logLevelSerializer = new BaseSerializerFactory().getSerializerFor(LogLevel.class);
            classPathSerializer = new ListSerializer<>(FILE_SERIALIZER);
        }

        @Override
        public void write(Encoder encoder, BuildActionParameters parameters) throws Exception {
            FILE_SERIALIZER.write(encoder, parameters.getCurrentDir());
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, parameters.getSystemProperties());
            NO_NULL_STRING_MAP_SERIALIZER.write(encoder, parameters.getEnvVariables());
            logLevelSerializer.write(encoder, parameters.getLogLevel());
            encoder.writeBoolean(parameters.isUseDaemon()); // Can probably skip this
            classPathSerializer.write(encoder, parameters.getInjectedPluginClasspath().getAsFiles());
        }

        @Override
        public BuildActionParameters read(Decoder decoder) throws Exception {
            File currentDir = FILE_SERIALIZER.read(decoder);
            Map<String, String> sysProperties = NO_NULL_STRING_MAP_SERIALIZER.read(decoder);
            Map<String, String> envVariables = NO_NULL_STRING_MAP_SERIALIZER.read(decoder);
            LogLevel logLevel = logLevelSerializer.read(decoder);
            boolean useDaemon = decoder.readBoolean();
            ClassPath classPath = DefaultClassPath.of(classPathSerializer.read(decoder));
            return new DefaultBuildActionParameters(sysProperties, envVariables, currentDir, logLevel, useDaemon, classPath);
        }
    }

    private static class DaemonUnavailableSerializer implements Serializer<DaemonUnavailable> {
        @Override
        public void write(Encoder encoder, DaemonUnavailable value) throws Exception {
            encoder.writeNullableString(value.getReason());
        }

        @Override
        public DaemonUnavailable read(Decoder decoder) throws Exception {
            return new DaemonUnavailable(decoder.readNullableString());
        }
    }

    private static class CancelSerializer implements Serializer<Cancel> {
        @Override
        public void write(Encoder encoder, Cancel value) {
        }

        @Override
        public Cancel read(Decoder decoder) {
            return new Cancel();
        }
    }

    private static class BuildStartedSerializer implements Serializer<BuildStarted> {
        @Override
        public void write(Encoder encoder, BuildStarted buildStarted) throws Exception {
            FILE_SERIALIZER.write(encoder, buildStarted.getDiagnostics().getDaemonLog());
            if (buildStarted.getDiagnostics().getPid() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeLong(buildStarted.getDiagnostics().getPid());
            }
        }

        @Override
        public BuildStarted read(Decoder decoder) throws Exception {
            File log = FILE_SERIALIZER.read(decoder);
            boolean nonNull = decoder.readBoolean();
            Long pid = null;
            if (nonNull) {
                pid = decoder.readLong();
            }
            return new BuildStarted(new DaemonDiagnostics(log, pid));
        }
    }

    private static class FinishedSerializer implements Serializer<Finished> {
        @Override
        public Finished read(Decoder decoder) {
            return new Finished();
        }

        @Override
        public void write(Encoder encoder, Finished value) {
        }
    }
}
