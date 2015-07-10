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
import org.gradle.internal.serialize.*;
import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.OutputEvent;

public class DaemonMessageSerializer {
    public static Serializer<Object> create() {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        DefaultSerializerRegistry<Object> registry = new DefaultSerializerRegistry<Object>();
        registry.register(BuildEvent.class, new BuildEventSerializer());
        registry.register(LogEvent.class, new LogEventSerializer(factory.getSerializerFor(LogLevel.class), factory.getSerializerFor(Throwable.class)));
        registry.useJavaSerialization(Message.class);
        registry.useJavaSerialization(OutputEvent.class);
        return registry.build();
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
}
