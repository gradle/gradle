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
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class AnnotationProcessingData {
    private final Map<String, Set<String>> generatedTypesByOrigin;
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
        this.aggregatedTypes = ImmutableSet.copyOf(aggregatedTypes);
        this.generatedTypesDependingOnAllOthers = ImmutableSet.copyOf(generatedTypesDependingOnAllOthers);
        this.generatedResourcesByOrigin = ImmutableMap.copyOf(generatedResourcesByOrigin);
        this.generatedResourcesDependingOnAllOthers = ImmutableSet.copyOf(generatedResourcesDependingOnAllOthers);
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

    public Map<String, Set<GeneratedResource>> getGeneratedResourcesByOrigin() {
        return generatedResourcesByOrigin;
    }

    public Set<GeneratedResource> getGeneratedResourcesDependingOnAllOthers() {
        return generatedResourcesDependingOnAllOthers;
    }

    public String getFullRebuildCause() {
        return fullRebuildCause;
    }

    public static final class Serializer extends AbstractSerializer<AnnotationProcessingData> {

        private final Supplier<HierarchicalNameSerializer> classNameSerializerSupplier;

        public Serializer(Supplier<HierarchicalNameSerializer> classNameSerializerSupplier) {
            this.classNameSerializerSupplier = classNameSerializerSupplier;
        }

        @Override
        public AnnotationProcessingData read(Decoder decoder) throws Exception {
            HierarchicalNameSerializer hierarchicalNameSerializer = classNameSerializerSupplier.get();
            SetSerializer<String> typesSerializer = new SetSerializer<>(hierarchicalNameSerializer);
            MapSerializer<String, Set<String>> generatedTypesSerializer = new MapSerializer<>(hierarchicalNameSerializer, typesSerializer);
            GeneratedResourceSerializer resourceSerializer = new GeneratedResourceSerializer(hierarchicalNameSerializer);
            SetSerializer<GeneratedResource> resourcesSerializer = new SetSerializer<>(resourceSerializer);
            MapSerializer<String, Set<GeneratedResource>> generatedResourcesSerializer = new MapSerializer<>(hierarchicalNameSerializer, resourcesSerializer);


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
            HierarchicalNameSerializer hierarchicalNameSerializer = classNameSerializerSupplier.get();
            SetSerializer<String> typesSerializer = new SetSerializer<>(hierarchicalNameSerializer);
            MapSerializer<String, Set<String>> generatedTypesSerializer = new MapSerializer<>(hierarchicalNameSerializer, typesSerializer);
            GeneratedResourceSerializer resourceSerializer = new GeneratedResourceSerializer(hierarchicalNameSerializer);
            SetSerializer<GeneratedResource> resourcesSerializer = new SetSerializer<>(resourceSerializer);
            MapSerializer<String, Set<GeneratedResource>> generatedResourcesSerializer = new MapSerializer<>(hierarchicalNameSerializer, resourcesSerializer);

            generatedTypesSerializer.write(encoder, value.generatedTypesByOrigin);
            typesSerializer.write(encoder, value.aggregatedTypes);
            typesSerializer.write(encoder, value.generatedTypesDependingOnAllOthers);
            encoder.writeNullableString(value.fullRebuildCause);
            generatedResourcesSerializer.write(encoder, value.generatedResourcesByOrigin);
            resourcesSerializer.write(encoder, value.generatedResourcesDependingOnAllOthers);
        }
    }
}
