/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

public class OutputEventSerializer {

    public static void registerTypes(DefaultSerializerRegistry registry) {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        Serializer<LogLevel> logLevelSerializer = factory.getSerializerFor(LogLevel.class);
        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);

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
    }

    public static Serializer<OutputEvent> create() {
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();
        registerTypes(registry);
        return registry.build(OutputEvent.class);
    }
}
