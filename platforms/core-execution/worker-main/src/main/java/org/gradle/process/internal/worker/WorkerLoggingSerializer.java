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

package org.gradle.process.internal.worker;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.serializer.LogEventSerializer;
import org.gradle.internal.logging.serializer.LogLevelChangeEventSerializer;
import org.gradle.internal.logging.serializer.SpanSerializer;
import org.gradle.internal.logging.serializer.StyledTextOutputEventSerializer;
import org.gradle.internal.logging.serializer.TimestampSerializer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.time.Timestamp;

public class WorkerLoggingSerializer {

    public static SerializerRegistry create() {
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry(false);

        BaseSerializerFactory factory = new BaseSerializerFactory();
        Serializer<LogLevel> logLevelSerializer = factory.getSerializerFor(LogLevel.class);
        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);
        Serializer<Timestamp> timestampSerializer = new TimestampSerializer();

        // Log events
        registry.register(LogEvent.class, new LogEventSerializer(timestampSerializer, logLevelSerializer, throwableSerializer));
        registry.register(StyledTextOutputEvent.class, new StyledTextOutputEventSerializer(timestampSerializer, logLevelSerializer, new ListSerializer<StyledTextOutputEvent.Span>(new SpanSerializer(factory.getSerializerFor(StyledTextOutput.Style.class)))));
        registry.register(LogLevelChangeEvent.class, new LogLevelChangeEventSerializer(logLevelSerializer));

        return registry;
    }
}
