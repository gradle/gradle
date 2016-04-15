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
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.events.*;
import org.gradle.internal.serialize.*;

import java.util.List;

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

    private static class ProgressEventSerializer implements Serializer<ProgressEvent> {
        @Override
        public void write(Encoder encoder, ProgressEvent event) throws Exception {
            encoder.writeSmallLong(event.getOperationId().getId());
            encoder.writeLong(event.getTimestamp());
            encoder.writeString(event.getCategory());
            encoder.writeString(event.getStatus());
        }

        @Override
        public ProgressEvent read(Decoder decoder) throws Exception {
            OperationIdentifier id = new OperationIdentifier(decoder.readSmallLong());
            long timestamp = decoder.readLong();
            String category = decoder.readString();
            String status = decoder.readString();
            return new ProgressEvent(id, timestamp, category, status);
        }
    }

    private static class ProgressCompleteEventSerializer implements Serializer<ProgressCompleteEvent> {
        @Override
        public void write(Encoder encoder, ProgressCompleteEvent event) throws Exception {
            encoder.writeSmallLong(event.getOperationId().getId());
            encoder.writeLong(event.getTimestamp());
            encoder.writeString(event.getCategory());
            encoder.writeString(event.getDescription());
            encoder.writeString(event.getStatus());
        }

        @Override
        public ProgressCompleteEvent read(Decoder decoder) throws Exception {
            OperationIdentifier id = new OperationIdentifier(decoder.readSmallLong());
            long timestamp = decoder.readLong();
            String category = decoder.readString();
            String description = decoder.readString();
            String status = decoder.readString();
            return new ProgressCompleteEvent(id, timestamp, category, description, status);
        }
    }

    private static class LogLevelChangeEventSerializer implements Serializer<LogLevelChangeEvent> {
        private final Serializer<LogLevel> logLevelSerializer;

        public LogLevelChangeEventSerializer(Serializer<LogLevel> logLevelSerializer) {
            this.logLevelSerializer = logLevelSerializer;
        }

        @Override
        public void write(Encoder encoder, LogLevelChangeEvent value) throws Exception {
            logLevelSerializer.write(encoder, value.getNewLogLevel());
        }

        @Override
        public LogLevelChangeEvent read(Decoder decoder) throws Exception {
            LogLevel logLevel = logLevelSerializer.read(decoder);
            return new LogLevelChangeEvent(logLevel);
        }
    }

    private static class LogEventSerializer implements Serializer<LogEvent> {
        private final Serializer<Throwable> throwableSerializer;
        private final Serializer<LogLevel> logLevelSerializer;

        public LogEventSerializer(Serializer<LogLevel> logLevelSerializer, Serializer<Throwable> throwableSerializer) {
            this.logLevelSerializer = logLevelSerializer;
            this.throwableSerializer = throwableSerializer;
        }

        @Override
        public void write(Encoder encoder, LogEvent event) throws Exception {
            encoder.writeLong(event.getTimestamp());
            encoder.writeString(event.getCategory());
            logLevelSerializer.write(encoder, event.getLogLevel());
            encoder.writeString(event.getMessage());
            throwableSerializer.write(encoder, event.getThrowable());
        }

        @Override
        public LogEvent read(Decoder decoder) throws Exception {
            long timestamp = decoder.readLong();
            String category = decoder.readString();
            LogLevel logLevel = logLevelSerializer.read(decoder);
            String message = decoder.readString();
            Throwable throwable = throwableSerializer.read(decoder);
            return new LogEvent(timestamp, category, logLevel, message, throwable);
        }
    }

    private static class SpanSerializer implements Serializer<StyledTextOutputEvent.Span> {
        private final Serializer<StyledTextOutput.Style> styleSerializer;

        public SpanSerializer(Serializer<StyledTextOutput.Style> styleSerializer) {
            this.styleSerializer = styleSerializer;
        }

        @Override
        public void write(Encoder encoder, StyledTextOutputEvent.Span value) throws Exception {
            styleSerializer.write(encoder, value.getStyle());
            encoder.writeString(value.getText());
        }

        @Override
        public StyledTextOutputEvent.Span read(Decoder decoder) throws Exception {
            return new StyledTextOutputEvent.Span(styleSerializer.read(decoder), decoder.readString());
        }
    }

    private static class StyledTextOutputEventSerializer implements Serializer<StyledTextOutputEvent> {
        private final Serializer<LogLevel> logLevelSerializer;
        private final Serializer<List<StyledTextOutputEvent.Span>> spanSerializer;

        public StyledTextOutputEventSerializer(Serializer<LogLevel> logLevelSerializer, Serializer<List<StyledTextOutputEvent.Span>> spanSerializer) {
            this.logLevelSerializer = logLevelSerializer;
            this.spanSerializer = spanSerializer;
        }

        @Override
        public void write(Encoder encoder, StyledTextOutputEvent event) throws Exception {
            encoder.writeLong(event.getTimestamp());
            encoder.writeString(event.getCategory());
            logLevelSerializer.write(encoder, event.getLogLevel());
            spanSerializer.write(encoder, event.getSpans());
        }

        @Override
        public StyledTextOutputEvent read(Decoder decoder) throws Exception {
            long timestamp = decoder.readLong();
            String category = decoder.readString();
            LogLevel logLevel = logLevelSerializer.read(decoder);
            List<StyledTextOutputEvent.Span> spans = spanSerializer.read(decoder);
            return new StyledTextOutputEvent(timestamp, category, logLevel, spans);
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
