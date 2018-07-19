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

package org.gradle.api.internal.tasks.compile.incremental.processing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Map;
import java.util.Set;

public class AnnotationProcessingData {
    private final Map<String, Set<String>> generatedTypesByOrigin;
    private final Set<String> aggregatedTypes;
    private final Set<String> generatedTypesDependingOnAllOthers;
    private final String fullRebuildCause;

    public AnnotationProcessingData() {
        this(ImmutableMap.<String, Set<String>>of(), ImmutableSet.<String>of(), ImmutableSet.<String>of(), null);
    }

    public AnnotationProcessingData(Map<String, Set<String>> generatedTypesByOrigin, Set<String> aggregatedTypes, Set<String> generatedTypesDependingOnAllOthers, String fullRebuildCause) {
        this.generatedTypesByOrigin = ImmutableMap.copyOf(generatedTypesByOrigin);
        this.aggregatedTypes = ImmutableSet.copyOf(aggregatedTypes);
        this.generatedTypesDependingOnAllOthers = ImmutableSet.copyOf(generatedTypesDependingOnAllOthers);
        this.fullRebuildCause = fullRebuildCause;
    }

    public Map<String, Set<String>> getGeneratedTypesByOrigin() {
        return generatedTypesByOrigin;
    }

    public Set<String> getAggregatedTypes() {
        return aggregatedTypes;
    }

    public Set<String> getGeneratedTypesDependingOnAllOthers() {
        return generatedTypesDependingOnAllOthers;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public static final class Serializer extends AbstractSerializer<AnnotationProcessingData> {
        private static final SetSerializer<String> STRING_SET_SERIALIZER = new SetSerializer<String>(BaseSerializerFactory.STRING_SERIALIZER);
        private static final MapSerializer<String, Set<String>> GENERATED_TYPES_SERIALIZER = new MapSerializer<String, Set<String>>(BaseSerializerFactory.STRING_SERIALIZER, STRING_SET_SERIALIZER);

        @Override
        public AnnotationProcessingData read(Decoder decoder) throws Exception {
            Map<String, Set<String>> generatedTypes = GENERATED_TYPES_SERIALIZER.read(decoder);
            Set<String> aggregatedTypes = STRING_SET_SERIALIZER.read(decoder);
            Set<String> generatedTypesDependingOnAllOthers = STRING_SET_SERIALIZER.read(decoder);
            String fullRebuildCause = decoder.readNullableString();
            return new AnnotationProcessingData(generatedTypes, aggregatedTypes, generatedTypesDependingOnAllOthers, fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, AnnotationProcessingData value) throws Exception {
            GENERATED_TYPES_SERIALIZER.write(encoder, value.generatedTypesByOrigin);
            STRING_SET_SERIALIZER.write(encoder, value.aggregatedTypes);
            STRING_SET_SERIALIZER.write(encoder, value.generatedTypesDependingOnAllOthers);
            encoder.writeNullableString(value.fullRebuildCause);
        }
    }
}
