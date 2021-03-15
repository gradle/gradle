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
import com.google.common.collect.Sets;
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
    private final Map<String, String> generatedTypesToOrigin;
    private final Set<String> aggregatedTypes;
    private final Set<String> generatedTypesDependingOnAllOthers;
    private final Map<String, Set<GeneratedResource>> generatedResourcesByOrigin;
    private final Set<GeneratedResource> generatedResourcesDependingOnAllOthers;
    private final String fullRebuildCause;

    public AnnotationProcessingData() {
        this(ImmutableMap.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableMap.of(), ImmutableSet.of(), null);
    }

    public AnnotationProcessingData(Map<String, Set<String>> generatedTypesByOrigin, Set<String> aggregatedTypes, Set<String> generatedTypesDependingOnAllOthers, Map<String,
        Set<GeneratedResource>> generatedResourcesByOrigin, Set<GeneratedResource> generatedResourcesDependingOnAllOthers, String fullRebuildCause) {

        this.generatedTypesByOrigin = ImmutableMap.copyOf(generatedTypesByOrigin);
        this.generatedTypesToOrigin = buildGeneratedTypesToOrigin(generatedTypesByOrigin);
        this.aggregatedTypes = ImmutableSet.copyOf(aggregatedTypes);
        this.generatedTypesDependingOnAllOthers = ImmutableSet.copyOf(generatedTypesDependingOnAllOthers);
        this.generatedResourcesByOrigin = ImmutableMap.copyOf(generatedResourcesByOrigin);
        this.generatedResourcesDependingOnAllOthers = ImmutableSet.copyOf(generatedResourcesDependingOnAllOthers);
        this.fullRebuildCause = fullRebuildCause;
    }

    private Map<String, String> buildGeneratedTypesToOrigin(Map<String, Set<String>> generatedTypesByOrigin) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        Set<String> seen = Sets.newHashSet();
        for (Map.Entry<String, Set<String>> entry : generatedTypesByOrigin.entrySet()) {
            String origin = entry.getKey();
            for (String generatedType : entry.getValue()) {
                // Guava's builder doesn't support duplicates but we handle them separately
                if (seen.add(generatedType)) {
                    builder.put(generatedType, origin);
                }
            }
        }
        return builder.build();
    }

    public boolean participatesInClassGeneration(String clazzName) {
        return aggregatedTypes.contains(clazzName) || generatedTypesByOrigin.containsKey(clazzName);
    }

    public boolean participatesInResourceGeneration(String clazzName) {
        return participatesInClassGeneration(clazzName) || generatedResourcesByOrigin.containsKey(clazzName);
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

    public Map<String, Set<GeneratedResource>> getGeneratedResourcesByOrigin() {
        return generatedResourcesByOrigin;
    }

    public Set<GeneratedResource> getGeneratedResourcesDependingOnAllOthers() {
        return generatedResourcesDependingOnAllOthers;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public String getOriginOf(String type) {
        // if we can't find a source, then the type to reprocess is the type itself
        return generatedTypesToOrigin.getOrDefault(type, type);
    }

    public static final class Serializer extends AbstractSerializer<AnnotationProcessingData> {
        private final SetSerializer<String> typesSerializer;
        private final MapSerializer<String, Set<String>> generatedTypesSerializer;
        private final SetSerializer<GeneratedResource> resourcesSerializer;
        private final MapSerializer<String, Set<GeneratedResource>> generatedResourcesSerializer;

        public Serializer() {
            typesSerializer = new SetSerializer<>(BaseSerializerFactory.STRING_SERIALIZER);
            generatedTypesSerializer = new MapSerializer<>(BaseSerializerFactory.STRING_SERIALIZER, typesSerializer);

            GeneratedResourceSerializer resourceSerializer = new GeneratedResourceSerializer(BaseSerializerFactory.STRING_SERIALIZER);
            this.resourcesSerializer = new SetSerializer<>(resourceSerializer);
            this.generatedResourcesSerializer = new MapSerializer<>(BaseSerializerFactory.STRING_SERIALIZER, resourcesSerializer);
        }

        @Override
        public AnnotationProcessingData read(Decoder decoder) throws Exception {
            Map<String, Set<String>> generatedTypes = generatedTypesSerializer.read(decoder);
            Set<String> aggregatedTypes = typesSerializer.read(decoder);
            Set<String> generatedTypesDependingOnAllOthers = typesSerializer.read(decoder);
            String fullRebuildCause = decoder.readNullableString();
            Map<String, Set<GeneratedResource>> generatedResources = generatedResourcesSerializer.read(decoder);
            Set<GeneratedResource> generatedResourcesDependingOnAllOthers = resourcesSerializer.read(decoder);

            return new AnnotationProcessingData(generatedTypes, aggregatedTypes, generatedTypesDependingOnAllOthers, generatedResources, generatedResourcesDependingOnAllOthers, fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, AnnotationProcessingData value) throws Exception {
            generatedTypesSerializer.write(encoder, value.generatedTypesByOrigin);
            typesSerializer.write(encoder, value.aggregatedTypes);
            typesSerializer.write(encoder, value.generatedTypesDependingOnAllOthers);
            encoder.writeNullableString(value.fullRebuildCause);
            generatedResourcesSerializer.write(encoder, value.generatedResourcesByOrigin);
            resourcesSerializer.write(encoder, value.generatedResourcesDependingOnAllOthers);
        }
    }
}
