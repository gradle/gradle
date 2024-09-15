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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
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
import org.gradle.util.Path;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.Set;

/**
 * A serializer for {@link ComponentSelector}s.
 */
@NotThreadSafe
public class ComponentSelectorSerializer extends AbstractSerializer<ComponentSelector> {
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final BuildIdentifierSerializer buildIdentifierSerializer;
    private final CapabilitySelectorSerializer capabilitySelectorSerializer;
    private final ModuleComponentSelectorSerializer moduleComponentSelectorSerializer;

    public ComponentSelectorSerializer(AttributeContainerSerializer attributeContainerSerializer, CapabilitySelectorSerializer capabilitySelectorSerializer) {
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySelectorSerializer = capabilitySelectorSerializer;

        this.buildIdentifierSerializer = new BuildIdentifierSerializer();
        this.moduleComponentSelectorSerializer = new ModuleComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer);
    }

    @Override
    public ComponentSelector read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if (Implementation.ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            String projectName = decoder.readString();
            ProjectIdentity projectId = new ProjectIdentity(buildIdentifier, Path.ROOT, Path.ROOT, projectName);
            ImmutableAttributes attributes = readAttributes(decoder);
            return new DefaultProjectComponentSelector(projectId, attributes, readCapabilitySelectors(decoder));
        } else if (Implementation.ROOT_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path projectPath = Path.path(decoder.readString());
            ProjectIdentity projectId = new ProjectIdentity(buildIdentifier, projectPath, projectPath, projectPath.getName());
            ImmutableAttributes attributes = readAttributes(decoder);
            return new DefaultProjectComponentSelector(projectId, attributes, readCapabilitySelectors(decoder));
        } else if (Implementation.OTHER_BUILD_ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            String projectName = decoder.readString();
            ProjectIdentity projectId = new ProjectIdentity(buildIdentifier, identityPath, Path.ROOT, projectName);
            ImmutableAttributes attributes = readAttributes(decoder);
            return new DefaultProjectComponentSelector(projectId, attributes, readCapabilitySelectors(decoder));
        } else if (Implementation.OTHER_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            Path projectPath = Path.path(decoder.readString());
            ProjectIdentity projectId = new ProjectIdentity(buildIdentifier, identityPath, projectPath, projectPath.getName());
            ImmutableAttributes attributes = readAttributes(decoder);
            return new DefaultProjectComponentSelector(projectId, attributes, readCapabilitySelectors(decoder));
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
        } else if (implementation == Implementation.ROOT_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getProjectIdentity().getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getProjectIdentity().getProjectName());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilitySelectors(encoder, projectComponentSelector.getCapabilitySelectors());
        } else if (implementation == Implementation.ROOT_BUILD_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getProjectIdentity().getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getProjectPath());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilitySelectors(encoder, projectComponentSelector.getCapabilitySelectors());
        } else if (implementation == Implementation.OTHER_BUILD_ROOT_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getProjectIdentity().getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getIdentityPath().getPath());
            encoder.writeString(projectComponentSelector.getProjectIdentity().getProjectName());
            writeAttributes(encoder, projectComponentSelector.getAttributes());
            writeCapabilitySelectors(encoder, projectComponentSelector.getCapabilitySelectors());
        } else if (implementation == Implementation.OTHER_BUILD_PROJECT) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            buildIdentifierSerializer.write(encoder, projectComponentSelector.getProjectIdentity().getBuildIdentifier());
            encoder.writeString(projectComponentSelector.getIdentityPath().getPath());
            encoder.writeString(projectComponentSelector.getProjectPath());
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

    private void writeAttributes(Encoder encoder, AttributeContainer attributes) throws IOException {
        attributeContainerSerializer.write(encoder, attributes);
    }

    private ComponentSelectorSerializer.Implementation resolveImplementation(ComponentSelector value) {
        Implementation implementation;

        if (value instanceof DefaultModuleComponentSelector) {
            implementation = Implementation.MODULE;
        } else if (value instanceof DefaultProjectComponentSelector) {
            DefaultProjectComponentSelector projectComponentSelector = (DefaultProjectComponentSelector) value;
            // Special case some common combinations of names and paths
            boolean isARootProject = projectComponentSelector.getProjectIdentity().getProjectPath().equals(Path.ROOT);
            if (projectComponentSelector.getIdentityPath().equals(Path.ROOT) && isARootProject) {
                return Implementation.ROOT_PROJECT;
            }
            if (isARootProject) {
                return Implementation.OTHER_BUILD_ROOT_PROJECT;
            }
            // For non-root project, project name must be the last element of the project path
            Path projectPath = projectComponentSelector.getProjectIdentity().getProjectPath();
            String projectName = projectComponentSelector.getProjectIdentity().getProjectName();
            if (!projectName.equals(projectPath.getName())) {
                throw new IllegalArgumentException("Unexpected name for project " + projectPath + ". Expected: " + projectPath.getName() + ", found: " + projectName);
            }
            if (projectComponentSelector.getIdentityPath().equals(projectPath)) {
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

}
