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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.local.model.DefaultLibraryComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.Path;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ComponentSelectorSerializer extends AbstractSerializer<ComponentSelector> {
    private final OptimizingAttributeContainerSerializer attributeContainerSerializer;
    private final BuildIdentifierSerializer buildIdentifierSerializer;

    public ComponentSelectorSerializer(AttributeContainerSerializer attributeContainerSerializer) {
        this.attributeContainerSerializer = new OptimizingAttributeContainerSerializer(attributeContainerSerializer);
        this.buildIdentifierSerializer = new BuildIdentifierSerializer();
    }

    void reset() {
        attributeContainerSerializer.reset();
    }

    @Override
    public ComponentSelector read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if (Implementation.ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            String projectName = decoder.readString();
            return new DefaultProjectComponentSelector(buildIdentifier, Path.ROOT, Path.ROOT, projectName, readAttributes(decoder), readCapabilities(decoder));
        } else if (Implementation.ROOT_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path projectPath = Path.path(decoder.readString());
            return new DefaultProjectComponentSelector(buildIdentifier, projectPath, projectPath, projectPath.getName(), readAttributes(decoder), readCapabilities(decoder));
        } else if (Implementation.OTHER_BUILD_ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            String projectName = decoder.readString();
            return new DefaultProjectComponentSelector(buildIdentifier, identityPath, Path.ROOT, projectName, readAttributes(decoder), readCapabilities(decoder));
        } else if (Implementation.OTHER_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            Path projectPath = Path.path(decoder.readString());
            return new DefaultProjectComponentSelector(buildIdentifier, identityPath, projectPath, projectPath.getName(), readAttributes(decoder), readCapabilities(decoder));
        } else if (Implementation.MODULE.getId() == id) {
            return DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), readVersionConstraint(decoder), readAttributes(decoder), readCapabilities(decoder));
        } else if (Implementation.LIBRARY.getId() == id) {
            return new DefaultLibraryComponentSelector(decoder.readString(), decoder.readNullableString(), decoder.readNullableString());
        }

        throw new IllegalArgumentException("Unable to find component selector with id: " + id);
    }

    private ImmutableAttributes readAttributes(Decoder decoder) throws IOException {
        return attributeContainerSerializer.read(decoder);
    }

    ImmutableVersionConstraint readVersionConstraint(Decoder decoder) throws IOException {
        String requires = decoder.readString();
        String prefers = decoder.readString();
        String strictly = decoder.readString();
        int rejectCount = decoder.readSmallInt();
        List<String> rejects = Lists.newArrayListWithCapacity(rejectCount);
        for (int i = 0; i < rejectCount; i++) {
            rejects.add(decoder.readString());
        }
        String branch = decoder.readNullableString();
        return new DefaultImmutableVersionConstraint(prefers, requires, strictly, rejects, branch);
    }

    private List<Capability> readCapabilities(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Capability> builder = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            builder.add(new ImmutableCapability(decoder.readString(), decoder.readString(), decoder.readNullableString()));
        }
        return builder.build();
    }

    private void writeCapabilities(Encoder encoder, Collection<Capability> capabilities) throws IOException {
        encoder.writeSmallInt(capabilities.size());
        for (Capability capability : capabilities) {
            encoder.writeString(capability.getGroup());
            encoder.writeString(capability.getName());
            encoder.writeNullableString(capability.getVersion());
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
            ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector) value;
            encoder.writeString(moduleComponentSelector.getGroup());
            encoder.writeString(moduleComponentSelector.getModule());
            VersionConstraint versionConstraint = moduleComponentSelector.getVersionConstraint();
            writeVersionConstraint(encoder, versionConstraint);
            writeAttributes(encoder, moduleComponentSelector.getAttributes());
            writeCapabilities(encoder, moduleComponentSelector.getRequestedCapabilities());
        } else if (implementation == Implementation.ROOT_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getProjectName());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilities(encoder, projectComponentSelector.getRequestedCapabilities());
        } else if (implementation == Implementation.ROOT_BUILD_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getProjectPath());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilities(encoder, projectComponentSelector.getRequestedCapabilities());
        } else if (implementation == Implementation.OTHER_BUILD_ROOT_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getIdentityPath().getPath());
            encoder.writeString(projectComponentSelector.getProjectName());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilities(encoder, projectComponentSelector.getRequestedCapabilities());
        } else if (implementation == Implementation.OTHER_BUILD_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getIdentityPath().getPath());
            encoder.writeString(projectComponentSelector.getProjectPath());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilities(encoder, projectComponentSelector.getRequestedCapabilities());
        } else if (implementation == Implementation.LIBRARY) {
            LibraryComponentSelector libraryComponentSelector = (LibraryComponentSelector) value;
            encoder.writeString(libraryComponentSelector.getProjectPath());
            encoder.writeNullableString(libraryComponentSelector.getLibraryName());
            encoder.writeNullableString(libraryComponentSelector.getVariant());
        } else {
            throw new IllegalStateException("Unsupported implementation type: " + implementation);
        }
    }

    private void writeAttributes(Encoder encoder, AttributeContainer attributes) throws IOException {
        attributeContainerSerializer.write(encoder, attributes);
    }

    private void writeVersionConstraint(Encoder encoder, VersionConstraint versionConstraint) throws IOException {
        encoder.writeString(versionConstraint.getRequiredVersion());
        encoder.writeString(versionConstraint.getPreferredVersion());
        encoder.writeString(versionConstraint.getStrictVersion());
        List<String> rejectedVersions = versionConstraint.getRejectedVersions();
        encoder.writeSmallInt(rejectedVersions.size());
        for (String rejectedVersion : rejectedVersions) {
            encoder.writeString(rejectedVersion);
        }
        encoder.writeNullableString(versionConstraint.getBranch());
    }

    private ComponentSelectorSerializer.Implementation resolveImplementation(ComponentSelector value) {
        Implementation implementation;

        if (value instanceof DefaultModuleComponentSelector) {
            implementation = Implementation.MODULE;
        } else if (value instanceof DefaultProjectComponentSelector) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            // Special case some common combinations of names and paths
            boolean isARootProject = projectComponentSelector.projectPath().equals(Path.ROOT);
            if (projectComponentSelector.getIdentityPath().equals(Path.ROOT) && isARootProject) {
                return Implementation.ROOT_PROJECT;
            }
            if (isARootProject) {
                return Implementation.OTHER_BUILD_ROOT_PROJECT;
            }
            // For non-root project, project name must be the last element of the project path
            if (!projectComponentSelector.getProjectName().equals(projectComponentSelector.projectPath().getName())) {
                throw new IllegalArgumentException("Unexpected name for project " + projectComponentSelector.projectPath() + ". Expected: " + projectComponentSelector.projectPath().getName() + ", found: " + projectComponentSelector.getProjectName());
            }
            if (projectComponentSelector.getIdentityPath().equals(projectComponentSelector.projectPath())) {
                return Implementation.ROOT_BUILD_PROJECT;
            }
            return Implementation.OTHER_BUILD_PROJECT;
        } else if (value instanceof DefaultLibraryComponentSelector) {
            implementation = Implementation.LIBRARY;
        } else {
            throw new IllegalArgumentException("Unsupported component selector class: " + value.getClass());
        }

        return implementation;
    }

    private enum Implementation {
        MODULE(1), ROOT_PROJECT(2), ROOT_BUILD_PROJECT(3), OTHER_BUILD_ROOT_PROJECT(4), OTHER_BUILD_PROJECT(5), LIBRARY(6), SNAPSHOT(7);

        private final byte id;

        Implementation(int id) {
            this.id = (byte) id;
        }

        byte getId() {
            return id;
        }
    }

    private static class OptimizingAttributeContainerSerializer implements AttributeContainerSerializer {
        // We use an identity hashmap for performance, because we know that our attributes
        // are generated by a factory which guarantees same instances
        private final Map<AttributeContainer, Integer> writeIndex = Maps.newIdentityHashMap();
        private final List<ImmutableAttributes> readIndex = Lists.newArrayList();
        private final AttributeContainerSerializer delegate;

        private OptimizingAttributeContainerSerializer(AttributeContainerSerializer delegate) {
            this.delegate = delegate;
        }

        /**
         * If the cache expired, or that it contains too many entries,
         * it is possible for the same file to be read multiple times, in which
         * case the internal state of the reader _must_ be reset.
         */
        public void reset() {
            writeIndex.clear();
            readIndex.clear();
        }

        @Override
        public ImmutableAttributes read(Decoder decoder) throws IOException {
            boolean empty = decoder.readBoolean();
            if (empty) {
                return ImmutableAttributes.EMPTY;
            }
            int idx = decoder.readSmallInt();
            ImmutableAttributes attributes;
            if (idx == readIndex.size()) {
                // new entry
                attributes = delegate.read(decoder);
                readIndex.add(attributes);
            } else {
                attributes = readIndex.get(idx);
            }
            return attributes;
        }

        @Override
        public void write(Encoder encoder, AttributeContainer container) throws IOException {
            if (container.isEmpty()) {
                encoder.writeBoolean(true);
                return;
            } else {
                encoder.writeBoolean(false);
            }
            Integer idx = writeIndex.get(container);
            if (idx == null) {
                // new value
                encoder.writeSmallInt(writeIndex.size());
                writeIndex.put(container, writeIndex.size());
                delegate.write(encoder, container);
            } else {
                // known value, only write index
                encoder.writeSmallInt(idx);
            }
        }
    }

}
