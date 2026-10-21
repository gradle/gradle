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

package org.gradle.internal.logging.serializer;

import org.gradle.internal.logging.events.SelectOptionPromptEvent;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

import java.util.List;

public class SelectOptionPromptEventSerializer implements Serializer<SelectOptionPromptEvent> {
    private final Serializer<List<String>> optionsSerializer = new ListSerializer<String>(BaseSerializerFactory.STRING_SERIALIZER);

    @Override
    public void write(Encoder encoder, SelectOptionPromptEvent value) throws Exception {
        encoder.writeLong(value.getTimestamp());
        encoder.writeString(value.getQuestion());
        optionsSerializer.write(encoder, value.getOptions());
        encoder.writeSmallInt(value.getDefaultOption());
    }

    @Override
    public SelectOptionPromptEvent read(Decoder decoder) throws Exception {
        return new SelectOptionPromptEvent(decoder.readLong(), decoder.readString(), optionsSerializer.read(decoder), decoder.readSmallInt());
    }
}
