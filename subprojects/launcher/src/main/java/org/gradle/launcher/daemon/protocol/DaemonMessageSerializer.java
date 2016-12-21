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
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.serializer.LogEventSerializer;
import org.gradle.internal.logging.serializer.LogLevelChangeEventSerializer;
import org.gradle.internal.logging.serializer.ProgressCompleteEventSerializer;
import org.gradle.internal.logging.serializer.ProgressEventSerializer;
import org.gradle.internal.logging.serializer.SpanSerializer;
import org.gradle.internal.logging.serializer.StyledTextOutputEventSerializer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

public class DaemonMessageSerializer {
    public static Serializer<Message> create() {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        Serializer<LogLevel> logLevelSerializer = factory.getSerializerFor(LogLevel.class);
        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();

        registry.register(BuildEvent.class, new BuildEventSerializer());
        registry.register(Failure.class, new FailureSerializer(throwableSerializer));

        // Input events
        registry.register(ForwardInput.class, new ForwardInputSerializer());
        registry.register(CloseInput.class, new CloseInputSerializer());

        // Output events
        registry.register(LogEvent.class, new LogEventSerializer(logLevelSerializer, throwableSerializer));
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

    private static class FailureSerializer implements Serializer<Failure> {
        private final Serializer<Throwable> throwableSerializer;

        public FailureSerializer(Serializer<Throwable> throwableSerializer) {
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

    private static class ProgressStartEventSerializer implements Serializer<ProgressStartEvent> {
        @Override
        public void write(Encoder encoder, ProgressStartEvent event) throws Exception {
            encoder.writeSmallLong(event.getOperationId().getId());
            if (event.getParentId() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeSmallLong(event.getParentId().getId());
            }
            encoder.writeLong(event.getTimestamp());
            encoder.writeString(event.getCategory());
            encoder.writeString(event.getDescription());
            encoder.writeNullableString(event.getShortDescription());
            encoder.writeNullableString(event.getLoggingHeader());
            encoder.writeString(event.getStatus());
        }

        @Override
        public ProgressStartEvent read(Decoder decoder) throws Exception {
            OperationIdentifier id = new OperationIdentifier(decoder.readSmallLong());
            OperationIdentifier parentId = decoder.readBoolean() ? new OperationIdentifier(decoder.readSmallLong()) : null;
            long timestamp = decoder.readLong();
            String category = decoder.readString();
            String description = decoder.readString();
            String shortDescription = decoder.readNullableString();
            String loggingHeader = decoder.readNullableString();
            String status = decoder.readString();
            return new ProgressStartEvent(id, parentId, timestamp, category, description, shortDescription, loggingHeader, status);
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

        public OutputMessageSerializer(Serializer<OutputEvent> eventSerializer) {
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
}
