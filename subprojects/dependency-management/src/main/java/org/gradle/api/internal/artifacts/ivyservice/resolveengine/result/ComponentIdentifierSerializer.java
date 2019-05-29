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

import com.google.common.base.Objects;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.Path;

import java.io.IOException;

public class ComponentIdentifierSerializer extends AbstractSerializer<ComponentIdentifier> {
    private final BuildIdentifierSerializer buildIdentifierSerializer = new BuildIdentifierSerializer();

    @Override
    public ComponentIdentifier read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if (Implementation.ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            String projectName = decoder.readString();
            return new DefaultProjectComponentIdentifier(buildIdentifier, Path.ROOT, Path.ROOT, projectName);
        } else if (Implementation.ROOT_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path projectPath = Path.path(decoder.readString());
            return new DefaultProjectComponentIdentifier(buildIdentifier, projectPath, projectPath, projectPath.getName());
        } else if (Implementation.OTHER_BUILD_ROOT_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            return new DefaultProjectComponentIdentifier(buildIdentifier, identityPath, Path.ROOT, identityPath.getName());
        } else if (Implementation.OTHER_BUILD_PROJECT.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            Path identityPath = Path.path(decoder.readString());
            Path projectPath = Path.path(decoder.readString());
            return new DefaultProjectComponentIdentifier(buildIdentifier, identityPath, projectPath, identityPath.getName());
        } else if (Implementation.MODULE.getId() == id) {
            return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString());
        } else if (Implementation.SNAPSHOT.getId() == id) {
            return new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString(), decoder.readString());
        } else if (Implementation.LIBRARY.getId() == id) {
            return new DefaultLibraryBinaryIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
        }

        throw new IllegalArgumentException("Unable to find component identifier type with id: " + id);
    }

    @Override
    public void write(Encoder encoder, ComponentIdentifier value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Provided component identifier may not be null");
        }

        Implementation implementation = resolveImplementation(value);

        encoder.writeByte(implementation.getId());

        if (implementation == Implementation.MODULE) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) value;
            encoder.writeString(moduleComponentIdentifier.getGroup());
            encoder.writeString(moduleComponentIdentifier.getModule());
            encoder.writeString(moduleComponentIdentifier.getVersion());
        } else if (implementation == Implementation.SNAPSHOT) {
            MavenUniqueSnapshotComponentIdentifier snapshotIdentifier = (MavenUniqueSnapshotComponentIdentifier) value;
            encoder.writeString(snapshotIdentifier.getGroup());
            encoder.writeString(snapshotIdentifier.getModule());
            encoder.writeString(snapshotIdentifier.getVersion());
            encoder.writeString(snapshotIdentifier.getTimestamp());
        } else if (implementation == Implementation.ROOT_PROJECT) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) value;
            BuildIdentifier build = projectComponentIdentifier.getBuild();
            buildIdentifierSerializer.write(encoder, build);
            encoder.writeString(projectComponentIdentifier.getProjectName());
        } else if (implementation == Implementation.ROOT_BUILD_PROJECT) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) value;
            BuildIdentifier build = projectComponentIdentifier.getBuild();
            buildIdentifierSerializer.write(encoder, build);
            encoder.writeString(projectComponentIdentifier.getProjectPath());
        } else if (implementation == Implementation.OTHER_BUILD_ROOT_PROJECT) {
            DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
            BuildIdentifier build = projectComponentIdentifier.getBuild();
            buildIdentifierSerializer.write(encoder, build);
            encoder.writeString(projectComponentIdentifier.getIdentityPath().getPath());
        } else if (implementation == Implementation.OTHER_BUILD_PROJECT) {
            DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
            BuildIdentifier build = projectComponentIdentifier.getBuild();
            buildIdentifierSerializer.write(encoder, build);
            encoder.writeString(projectComponentIdentifier.getIdentityPath().getPath());
            encoder.writeString(projectComponentIdentifier.getProjectPath());
        } else if (implementation == Implementation.LIBRARY) {
            LibraryBinaryIdentifier libraryIdentifier = (LibraryBinaryIdentifier) value;
            encoder.writeString(libraryIdentifier.getProjectPath());
            encoder.writeString(libraryIdentifier.getLibraryName());
            encoder.writeString(libraryIdentifier.getVariant());
        } else {
            throw new IllegalStateException("Unsupported implementation type: " + implementation);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ComponentIdentifierSerializer rhs = (ComponentIdentifierSerializer) obj;
        return Objects.equal(buildIdentifierSerializer, rhs.buildIdentifierSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), buildIdentifierSerializer);
    }

    private Implementation resolveImplementation(ComponentIdentifier value) {
        if (value instanceof MavenUniqueSnapshotComponentIdentifier) {
            return Implementation.SNAPSHOT;
        } else if (value instanceof ModuleComponentIdentifier) {
            return Implementation.MODULE;
        } else if (value instanceof DefaultProjectComponentIdentifier) {
            DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
            // Special case some common combinations of names and paths
            boolean isARootProject = projectComponentIdentifier.projectPath().equals(Path.ROOT);
            if (projectComponentIdentifier.getIdentityPath().equals(Path.ROOT) && isARootProject) {
                return Implementation.ROOT_PROJECT;
            }
            if (projectComponentIdentifier.getIdentityPath().equals(projectComponentIdentifier.projectPath()) && projectComponentIdentifier.projectPath().getName().equals(projectComponentIdentifier.getProjectName())) {
                return Implementation.ROOT_BUILD_PROJECT;
            }
            if (isARootProject && projectComponentIdentifier.getProjectName().equals(projectComponentIdentifier.getIdentityPath().getName())) {
                return Implementation.OTHER_BUILD_ROOT_PROJECT;
            }
            return Implementation.OTHER_BUILD_PROJECT;
        } else if (value instanceof LibraryBinaryIdentifier) {
            return Implementation.LIBRARY;
        } else {
            throw new IllegalArgumentException("Unsupported component identifier class: " + value.getClass());
        }
    }

    private enum Implementation {
        MODULE(1), ROOT_PROJECT(2), ROOT_BUILD_PROJECT(3), OTHER_BUILD_ROOT_PROJECT(4), OTHER_BUILD_PROJECT(5), LIBRARY(6), SNAPSHOT(7);

        private final byte id;

        Implementation(int id) {
            this.id = (byte) id;
        }

        private byte getId() {
            return id;
        }
    }
}
