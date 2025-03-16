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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector;

public class ModuleComponentSelectorSerializer implements Serializer<ModuleComponentSelector> {
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final CapabilitySelectorSerializer capabilitySelectorSerializer;

    public ModuleComponentSelectorSerializer(
        AttributeContainerSerializer attributeContainerSerializer,
        CapabilitySelectorSerializer capabilitySelectorSerializer
    ) {
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySelectorSerializer = capabilitySelectorSerializer;
    }

    @Override
    public ModuleComponentSelector read(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String name = decoder.readString();
        VersionConstraint versionConstraint = readVersionConstraint(decoder);
        ImmutableAttributes attributes = readAttributes(decoder);
        ImmutableSet<CapabilitySelector> capabilitySelectors = readCapabilitySelectors(decoder);
        return newSelector(DefaultModuleIdentifier.newId(group, name), versionConstraint, attributes, capabilitySelectors);
    }

    public VersionConstraint readVersionConstraint(Decoder decoder) throws IOException {
        String required = decoder.readString();
        String preferred = decoder.readString();
        String strictly = decoder.readString();
        int cpt = decoder.readSmallInt();
        List<String> rejects = new ArrayList<>(cpt);
        for (int i = 0; i < cpt; i++) {
            rejects.add(decoder.readString());
        }
        String branch = decoder.readNullableString();
        return new DefaultImmutableVersionConstraint(preferred, required, strictly, rejects, branch);
    }

    @Override
    public void write(Encoder encoder, ModuleComponentSelector value) throws IOException {
        encoder.writeString(value.getGroup());
        encoder.writeString(value.getModule());
        writeVersionConstraint(encoder, value.getVersionConstraint());
        writeAttributes(encoder, ((AttributeContainerInternal)value.getAttributes()).asImmutable());
        writeCapabilitySelectors(encoder, value.getCapabilitySelectors());
    }

    public void write(Encoder encoder, String group, String module, VersionConstraint version, ImmutableAttributes attributes, Set<CapabilitySelector> capabilitySelectors) throws IOException {
        encoder.writeString(group);
        encoder.writeString(module);
        writeVersionConstraint(encoder, version);
        writeAttributes(encoder, attributes);
        writeCapabilitySelectors(encoder, capabilitySelectors);
    }

    public void writeVersionConstraint(Encoder encoder, VersionConstraint cst) throws IOException {
        encoder.writeString(cst.getRequiredVersion());
        encoder.writeString(cst.getPreferredVersion());
        encoder.writeString(cst.getStrictVersion());
        List<String> rejectedVersions = cst.getRejectedVersions();
        encoder.writeSmallInt(rejectedVersions.size());
        for (String rejectedVersion : rejectedVersions) {
            encoder.writeString(rejectedVersion);
        }
        encoder.writeNullableString(cst.getBranch());
    }

    private ImmutableAttributes readAttributes(Decoder decoder) throws IOException {
        return attributeContainerSerializer.read(decoder);
    }

    private void writeAttributes(Encoder encoder, ImmutableAttributes attributes) throws IOException {
        attributeContainerSerializer.write(encoder, attributes);
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
}
