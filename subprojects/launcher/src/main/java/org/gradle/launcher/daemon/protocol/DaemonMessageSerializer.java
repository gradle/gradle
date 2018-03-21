/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.serializer.LogEventSerializer;
import org.gradle.internal.logging.serializer.LogLevelChangeEventSerializer;
import org.gradle.internal.logging.serializer.ProgressCompleteEventSerializer;
import org.gradle.internal.logging.serializer.ProgressEventSerializer;
import org.gradle.internal.logging.serializer.ProgressStartEventSerializer;
import org.gradle.internal.logging.serializer.SpanSerializer;
import org.gradle.internal.logging.serializer.StyledTextOutputEventSerializer;
import org.gradle.internal.logging.serializer.UserInputRequestEventSerializer;
import org.gradle.internal.logging.serializer.UserInputResumeEventSerializer;
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

import java.io.File;
import java.util.UUID;

public class DaemonMessageSerializer {
    public static Serializer<Message> create() {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        Serializer<LogLevel> logLevelSerializer = factory.getSerializerFor(LogLevel.class);
        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();

        // Lifecycle messages
        registry.register(Build.class, new BuildSerializer());
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
        registry.register(CloseInput.class, new CloseInputSerializer());

        // Output events
        registry.register(LogEvent.class, new LogEventSerializer(logLevelSerializer, throwableSerializer));
        registry.register(UserInputRequestEvent.class, new UserInputRequestEventSerializer());
        registry.register(UserInputResumeEvent.class, new UserInputResumeEventSerializer());
        registry.register(StyledTextOutputEvent.class, new StyledTextOutputEventSerializer(logLevelSerializer, new ListSerializer<StyledTextOutputEvent.Span>(new SpanSerializer(factory.getSerializerFor(StyledTextOutput.Style.class)))));
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
        private final Serializer<Object> payloadSerializer = new DefaultSerializer<Object>();

        @Override
        public void write(Encoder encoder, Success success) throws Exception {
            if (success.getValue() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                payloadSerializer.write(encoder, success.getValue());
            }
        }

        @Override
        public Success read(Decoder decoder) throws Exception {
            boolean notNull = decoder.readBoolean();
            if (notNull) {
                return new Success(payloadSerializer.read(decoder));
            } else {
                return new Success(null);
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
        private final Serializer<Object> payloadSerializer = new DefaultSerializer<Object>();

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
        private final Serializer<Object[]> payloadSerializer = new DefaultSerializer<Object[]>();

        @Override
        public void write(Encoder encoder, Build build) throws Exception {
            encoder.writeLong(build.getIdentifier().getMostSignificantBits());
            encoder.writeLong(build.getIdentifier().getLeastSignificantBits());
            encoder.writeBinary(build.getToken());
            encoder.writeLong(build.getStartTime());
            // Use Java serialization for these pieces, pending some serializers for these types
            payloadSerializer.write(encoder, new Object[]{build.getAction(), build.getBuildClientMetaData(), build.getParameters()});
        }

        @Override
        public Build read(Decoder decoder) throws Exception {
            UUID uuid = new UUID(decoder.readLong(), decoder.readLong());
            byte[] token = decoder.readBinary();
            long timestamp = decoder.readLong();
            Object[] payload = payloadSerializer.read(decoder);
            return new Build(uuid, token, (BuildAction) payload[0], (BuildClientMetaData) payload[1], timestamp, (BuildActionParameters) payload[2]);
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
            encoder.writeString(buildStarted.getDiagnostics().getDaemonLog().getPath());
            if (buildStarted.getDiagnostics().getPid() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeLong(buildStarted.getDiagnostics().getPid());
            }
        }

        @Override
        public BuildStarted read(Decoder decoder) throws Exception {
            File log = new File(decoder.readString());
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
