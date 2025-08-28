/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.Set;

/**
 * A thread-safe and reusable serializer for {@link ComponentSelector}s.
 */
@NotThreadSafe
public class ComponentSelectorSerializer extends AbstractSerializer<ComponentSelector> {

    private final AttributeContainerSerializer attributeContainerSerializer;
    private final CapabilitySelectorSerializer capabilitySelectorSerializer;

    private final ProjectIdentitySerializer projectIdentitySerializer;
    private final ModuleComponentSelectorSerializer moduleComponentSelectorSerializer;

    public ComponentSelectorSerializer(AttributeContainerSerializer attributeContainerSerializer, CapabilitySelectorSerializer capabilitySelectorSerializer) {
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySelectorSerializer = capabilitySelectorSerializer;

        this.projectIdentitySerializer = new ProjectIdentitySerializer(new PathSerializer());
        this.moduleComponentSelectorSerializer = new ModuleComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer);
    }

    @Override
    public ComponentSelector read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if (Implementation.PROJECT.getId() == id) {
            ProjectIdentity projectId = projectIdentitySerializer.read(decoder);
            ImmutableAttributes attributes = readAttributes(decoder);
            ImmutableSet<CapabilitySelector> capabilitySelectors = readCapabilitySelectors(decoder);
            return new DefaultProjectComponentSelector(projectId, attributes, capabilitySelectors);
        } else if (Implementation.MODULE.getId() == id) {
            return moduleComponentSelectorSerializer.read(decoder);
        } else if (Implementation.LIBRARY.getId() == id) {
            return new DefaultLibraryComponentSelector(decoder.readString(), decoder.readNullableString(), decoder.readNullableString());
        }

        throw new IllegalArgumentException("Unable to find component selector with id: " + id);
    }

    private ImmutableAttributes readAttributes(Decoder decoder) throws IOException {
        return attributeContainerSerializer.read(decoder);
    }

    private ImmutableSet<CapabilitySelector> readCapabilitySelectors(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableSet.of();
        }
        ImmutableSet.Builder<CapabilitySelector> builder = ImmutableSet.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            builder.add(capabilitySelectorSerializer.read(decoder));
        }
        return builder.build();
    }

    private void writeCapabilitySelectors(Encoder encoder, Set<CapabilitySelector> capabilitySelectors) throws IOException {
        encoder.writeSmallInt(capabilitySelectors.size());
        for (CapabilitySelector capabilitySelector : capabilitySelectors) {
            capabilitySelectorSerializer.write(encoder, capabilitySelector);
        }
    }

    @Override
    public void write(Encoder encoder, ComponentSelector value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Provided component selector may not be null");
        }

        Implementation implementation = resolveImplementation(value);

        encoder.writeByte(implementation.getId());

        if (implementation == Implementation.MODULE) {
            moduleComponentSelectorSerializer.write(encoder, (ModuleComponentSelector) value);
        } else if (implementation == Implementation.PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            projectIdentitySerializer.write(encoder, projectComponentSelector.getProjectIdentity());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilitySelectors(encoder, projectComponentSelector.getCapabilitySelectors());
        } else if (implementation == Implementation.LIBRARY) {
            LibraryComponentSelector libraryComponentSelector = (LibraryComponentSelector) value;
            encoder.writeString(libraryComponentSelector.getProjectPath());
            encoder.writeNullableString(libraryComponentSelector.getLibraryName());
            encoder.writeNullableString(libraryComponentSelector.getVariant());
        } else {
            throw new IllegalStateException("Unsupported implementation type: " + implementation);
        }
    }

    private void writeAttributes(Encoder encoder, ImmutableAttributes attributes) throws IOException {
        attributeContainerSerializer.write(encoder, attributes);
    }

    private static Implementation resolveImplementation(ComponentSelector value) {
        if (value instanceof DefaultModuleComponentSelector) {
            return Implementation.MODULE;
        } else if (value instanceof DefaultProjectComponentSelector) {
            return Implementation.PROJECT;
        } else if (value instanceof DefaultLibraryComponentSelector) {
            return Implementation.LIBRARY;
        } else {
            throw new IllegalArgumentException("Unsupported component selector class: " + value.getClass());
        }
    }

    private enum Implementation {
        MODULE(1), PROJECT(2), LIBRARY(6);

        private final byte id;

        Implementation(int id) {
            this.id = (byte) id;
        }

        byte getId() {
            return id;
        }
    }

}
