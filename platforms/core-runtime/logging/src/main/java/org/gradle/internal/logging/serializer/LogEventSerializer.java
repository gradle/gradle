/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.serializer;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.Timestamp;

public class LogEventSerializer implements Serializer<LogEvent> {
    private final Serializer<Timestamp> timestampSerializer;
    private final Serializer<LogLevel> logLevelSerializer;
    private final Serializer<Throwable> throwableSerializer;

    public LogEventSerializer(Serializer<Timestamp> timestampSerializer, Serializer<LogLevel> logLevelSerializer, Serializer<Throwable> throwableSerializer) {
        this.timestampSerializer = timestampSerializer;
        this.logLevelSerializer = logLevelSerializer;
        this.throwableSerializer = throwableSerializer;
    }

    @Override
    public void write(Encoder encoder, LogEvent event) throws Exception {
        timestampSerializer.write(encoder, event.getTime());
        encoder.writeString(event.getCategory());
        logLevelSerializer.write(encoder, event.getLogLevel());
        encoder.writeNullableString(event.getMessage());
        throwableSerializer.write(encoder, event.getThrowable());
        if (event.getBuildOperationId() == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            encoder.writeSmallLong(event.getBuildOperationId().getId());
        }
    }

    @Override
    public LogEvent read(Decoder decoder) throws Exception {
        Timestamp timestamp = timestampSerializer.read(decoder);
        String category = decoder.readString();
        LogLevel logLevel = logLevelSerializer.read(decoder);
        String message = decoder.readNullableString();
        Throwable throwable = throwableSerializer.read(decoder);
        OperationIdentifier buildOperationId = decoder.readBoolean() ? new OperationIdentifier(decoder.readSmallLong()) : null;
        return new LogEvent(timestamp, category, logLevel, message, throwable, buildOperationId);
    }
}
