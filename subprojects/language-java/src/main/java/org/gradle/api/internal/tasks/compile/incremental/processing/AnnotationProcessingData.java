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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
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
        private final SetSerializer<String> typesSerializer;
        private final MapSerializer<String, Set<String>> generatedTypesSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            typesSerializer = new SetSerializer<String>(stringSerializer);
            generatedTypesSerializer = new MapSerializer<String, Set<String>>(stringSerializer, typesSerializer);
        }

        @Override
        public AnnotationProcessingData read(Decoder decoder) throws Exception {
            Map<String, Set<String>> generatedTypes = generatedTypesSerializer.read(decoder);
            Set<String> aggregatedTypes = typesSerializer.read(decoder);
            Set<String> generatedTypesDependingOnAllOthers = typesSerializer.read(decoder);
            String fullRebuildCause = decoder.readNullableString();
            return new AnnotationProcessingData(generatedTypes, aggregatedTypes, generatedTypesDependingOnAllOthers, fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, AnnotationProcessingData value) throws Exception {
            generatedTypesSerializer.write(encoder, value.generatedTypesByOrigin);
            typesSerializer.write(encoder, value.aggregatedTypes);
            typesSerializer.write(encoder, value.generatedTypesDependingOnAllOthers);
            encoder.writeNullableString(value.fullRebuildCause);
        }
    }
}
