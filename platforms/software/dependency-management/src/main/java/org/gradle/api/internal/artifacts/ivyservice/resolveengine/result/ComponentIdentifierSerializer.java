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
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link ComponentIdentifier}.
 */
public class ComponentIdentifierSerializer extends AbstractSerializer<ComponentIdentifier> {
    private final BuildIdentifierSerializer buildIdentifierSerializer = new BuildIdentifierSerializer();

    @Override
    public ComponentIdentifier read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();
        Implementation implementation = Implementation.valueOf(id);
        if (implementation == null) {
            throw new IllegalArgumentException("Unable to find component identifier type with id: " + id);
        }
        switch (implementation) {
            case ROOT_PROJECT: {
                BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
                String projectName = decoder.readString();
                ProjectIdentity projectIdentity = new ProjectIdentity(buildIdentifier, Path.ROOT, Path.ROOT, projectName);
                return new DefaultProjectComponentIdentifier(projectIdentity);
            }
            case ROOT_BUILD_PROJECT: {
                BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
                Path projectPath = Path.path(decoder.readString());
                ProjectIdentity projectIdentity = new ProjectIdentity(buildIdentifier, projectPath, projectPath, projectPath.getName());
                return new DefaultProjectComponentIdentifier(projectIdentity);
            }
            case OTHER_BUILD_ROOT_PROJECT: {
                BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
                Path identityPath = Path.path(decoder.readString());
                ProjectIdentity projectIdentity = new ProjectIdentity(buildIdentifier, identityPath, Path.ROOT, identityPath.getName());
                return new DefaultProjectComponentIdentifier(projectIdentity);
            }
            case OTHER_BUILD_PROJECT: {
                BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
                Path identityPath = Path.path(decoder.readString());
                Path projectPath = Path.path(decoder.readString());
                ProjectIdentity projectIdentity = new ProjectIdentity(buildIdentifier, identityPath, projectPath, identityPath.getName());
                return new DefaultProjectComponentIdentifier(projectIdentity);
            }
            case MODULE:
                return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString());
            case SNAPSHOT:
                return new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString(), decoder.readString());
            case LIBRARY:
                return new DefaultLibraryBinaryIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
            case OPAQUE:
                return new OpaqueComponentArtifactIdentifier(new File(decoder.readString()));
            case OPAQUE_NOTATION:
                return new OpaqueComponentIdentifier(readClassPathNotation(decoder));
            default:
                throw new IllegalArgumentException("Unsupported component identifier implementation: " + implementation);
        }
    }

    @Override
    public void write(Encoder encoder, ComponentIdentifier value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Provided component identifier may not be null");
        }

        Implementation implementation = resolveImplementation(value);

        encoder.writeByte(implementation.id);

        switch (implementation) {
            case MODULE:
                ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) value;
                encoder.writeString(moduleComponentIdentifier.getGroup());
                encoder.writeString(moduleComponentIdentifier.getModule());
                encoder.writeString(moduleComponentIdentifier.getVersion());
                break;
            case SNAPSHOT:
                MavenUniqueSnapshotComponentIdentifier snapshotIdentifier = (MavenUniqueSnapshotComponentIdentifier) value;
                encoder.writeString(snapshotIdentifier.getGroup());
                encoder.writeString(snapshotIdentifier.getModule());
                encoder.writeString(snapshotIdentifier.getVersion());
                encoder.writeString(snapshotIdentifier.getTimestamp());
                break;
            case ROOT_PROJECT: {
                ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) value;
                writeBuildIdentifierOf(projectComponentIdentifier, encoder);
                encoder.writeString(projectComponentIdentifier.getProjectName());
                break;
            }
            case ROOT_BUILD_PROJECT: {
                ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) value;
                writeBuildIdentifierOf(projectComponentIdentifier, encoder);
                encoder.writeString(projectComponentIdentifier.getProjectPath());
                break;
            }
            case OTHER_BUILD_ROOT_PROJECT: {
                DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
                writeBuildIdentifierOf(projectComponentIdentifier, encoder);
                encoder.writeString(projectComponentIdentifier.getIdentityPath().getPath());
                break;
            }
            case OTHER_BUILD_PROJECT: {
                DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
                writeBuildIdentifierOf(projectComponentIdentifier, encoder);
                encoder.writeString(projectComponentIdentifier.getIdentityPath().getPath());
                encoder.writeString(projectComponentIdentifier.getProjectPath());
                break;
            }
            case LIBRARY:
                LibraryBinaryIdentifier libraryIdentifier = (LibraryBinaryIdentifier) value;
                encoder.writeString(libraryIdentifier.getProjectPath());
                encoder.writeString(libraryIdentifier.getLibraryName());
                encoder.writeString(libraryIdentifier.getVariant());
                break;
            case OPAQUE: {
                OpaqueComponentArtifactIdentifier opaqueIdentifier = (OpaqueComponentArtifactIdentifier) value;
                encoder.writeString(opaqueIdentifier.getFile().getPath());
                break;
            }
            case OPAQUE_NOTATION: {
                OpaqueComponentIdentifier opaqueIdentifier = (OpaqueComponentIdentifier) value;
                writeClassPathNotation(encoder, opaqueIdentifier.getClassPathNotation());
                break;
            }
            default:
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

    private static void writeClassPathNotation(Encoder encoder, DependencyFactoryInternal.ClassPathNotation classPathNotation) throws IOException {
        encoder.writeSmallInt(classPathNotation.ordinal());
    }

    private static DependencyFactoryInternal.ClassPathNotation readClassPathNotation(Decoder decoder) throws IOException {
        int ordinal = decoder.readSmallInt();
        return DependencyFactoryInternal.ClassPathNotation.values()[ordinal];
    }

    private void writeBuildIdentifierOf(ProjectComponentIdentifier projectComponentIdentifier, Encoder encoder) throws IOException {
        buildIdentifierSerializer.write(encoder, projectComponentIdentifier.getBuild());
    }

    private Implementation resolveImplementation(ComponentIdentifier value) {
        if (value instanceof MavenUniqueSnapshotComponentIdentifier) {
            return Implementation.SNAPSHOT;
        } else if (value instanceof ModuleComponentIdentifier) {
            return Implementation.MODULE;
        } else if (value instanceof DefaultProjectComponentIdentifier) {
            DefaultProjectComponentIdentifier projectComponentIdentifier = (DefaultProjectComponentIdentifier) value;
            // Special case some common combinations of names and paths
            Path projectPath = projectComponentIdentifier.getProjectIdentity().getProjectPath();
            boolean isARootProject = projectPath.equals(Path.ROOT);
            if (projectComponentIdentifier.getIdentityPath().equals(Path.ROOT) && isARootProject) {
                return Implementation.ROOT_PROJECT;
            }
            if (projectComponentIdentifier.getIdentityPath().equals(projectPath) && projectPath.getName().equals(projectComponentIdentifier.getProjectName())) {
                return Implementation.ROOT_BUILD_PROJECT;
            }
            if (isARootProject && projectComponentIdentifier.getProjectName().equals(projectComponentIdentifier.getIdentityPath().getName())) {
                return Implementation.OTHER_BUILD_ROOT_PROJECT;
            }
            return Implementation.OTHER_BUILD_PROJECT;
        } else if (value instanceof LibraryBinaryIdentifier) {
            return Implementation.LIBRARY;
        } else if (value instanceof OpaqueComponentArtifactIdentifier) {
            return Implementation.OPAQUE;
        } else if (value instanceof OpaqueComponentIdentifier) {
            return Implementation.OPAQUE_NOTATION;
        } else {
            throw new IllegalArgumentException("Unsupported component identifier class: " + value.getClass());
        }
    }

    private enum Implementation {
        MODULE(1),
        ROOT_PROJECT(2),
        ROOT_BUILD_PROJECT(3),
        OTHER_BUILD_ROOT_PROJECT(4),
        OTHER_BUILD_PROJECT(5),
        LIBRARY(6),
        SNAPSHOT(7),
        OPAQUE(8),
        OPAQUE_NOTATION(9);

        @Nullable
        public static Implementation valueOf(int id) {
            Implementation[] values = values();
            if (id >= values[0].id && id <= values[values.length - 1].id) {
                return values[id - 1];
            }
            return null;
        }

        private final byte id;

        Implementation(int id) {
            this.id = (byte) id;
        }
    }
}
