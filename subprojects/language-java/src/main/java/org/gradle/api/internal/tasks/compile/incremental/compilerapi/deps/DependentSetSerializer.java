/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.util.function.Supplier;

public class DependentSetSerializer extends AbstractSerializer<DependentsSet> {
    private final Supplier<HierarchicalNameSerializer> hierarchicalNameSerializerSupplier;

    public DependentSetSerializer(Supplier<HierarchicalNameSerializer> hierarchicalNameSerializerSupplier) {
        this.hierarchicalNameSerializerSupplier = hierarchicalNameSerializerSupplier;
    }

    @Override
    public DependentsSet read(Decoder decoder) throws Exception {
        HierarchicalNameSerializer nameSerializer = hierarchicalNameSerializerSupplier.get();
        byte b = decoder.readByte();
        if (b == 0) {
            return DependentsSet.dependencyToAll(decoder.readString());
        }

        ImmutableSet.Builder<String> privateBuilder = ImmutableSet.builder();
        int count = decoder.readSmallInt();
        for (int i = 0; i < count; i++) {
            privateBuilder.add(nameSerializer.read(decoder));
        }

        ImmutableSet.Builder<String> accessibleBuilder = ImmutableSet.builder();
        count = decoder.readSmallInt();
        for (int i = 0; i < count; i++) {
            accessibleBuilder.add(nameSerializer.read(decoder));
        }

        ImmutableSet.Builder<GeneratedResource> resourceBuilder = ImmutableSet.builder();
        count = decoder.readSmallInt();
        for (int i = 0; i < count; i++) {
            GeneratedResource.Location location = GeneratedResource.Location.values()[decoder.readSmallInt()];
            String path = nameSerializer.read(decoder);
            resourceBuilder.add(new GeneratedResource(location, path));
        }
        return DependentsSet.dependents(privateBuilder.build(), accessibleBuilder.build(), resourceBuilder.build());
    }

    @Override
    public void write(Encoder encoder, DependentsSet dependentsSet) throws Exception {
        HierarchicalNameSerializer nameSerializer = hierarchicalNameSerializerSupplier.get();
        if (dependentsSet.isDependencyToAll()) {
            encoder.writeByte((byte) 0);
            encoder.writeString(dependentsSet.getDescription());
        } else {
            encoder.writeByte((byte) 1);
            encoder.writeSmallInt(dependentsSet.getPrivateDependentClasses().size());
            for (String className : dependentsSet.getPrivateDependentClasses()) {
                nameSerializer.write(encoder, className);
            }
            encoder.writeSmallInt(dependentsSet.getAccessibleDependentClasses().size());
            for (String className : dependentsSet.getAccessibleDependentClasses()) {
                nameSerializer.write(encoder, className);
            }
            encoder.writeSmallInt(dependentsSet.getDependentResources().size());
            for (GeneratedResource resource : dependentsSet.getDependentResources()) {
                encoder.writeSmallInt(resource.getLocation().ordinal());
                nameSerializer.write(encoder, resource.getPath());
            }
        }
    }
}
