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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultRootComponentIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link ComponentIdentifier}.
 */
public class ComponentIdentifierSerializer extends AbstractSerializer<ComponentIdentifier> {

    private final ProjectIdentitySerializer projectIdentitySerializer = new ProjectIdentitySerializer(new PathSerializer());

    @Override
    public ComponentIdentifier read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();
        Implementation implementation = Implementation.valueOf(id);
        if (implementation == null) {
            throw new IllegalArgumentException("Unable to find component identifier type with id: " + id);
        }
        switch (implementation) {
            case PROJECT:
                return new DefaultProjectComponentIdentifier(projectIdentitySerializer.read(decoder));
            case MODULE:
                return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString());
            case SNAPSHOT:
                return new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()), decoder.readString(), decoder.readString());
            case ROOT_COMPONENT: {
                long instanceId = decoder.readLong();
                return new DefaultRootComponentIdentifier(instanceId);
            }
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
            case PROJECT: {
                ProjectComponentIdentifierInternal projectComponentIdentifier = (ProjectComponentIdentifierInternal) value;
                projectIdentitySerializer.write(encoder, projectComponentIdentifier.getProjectIdentity());
                break;
            }
            case ROOT_COMPONENT: {
                DefaultRootComponentIdentifier rootComponentIdentifier = (DefaultRootComponentIdentifier) value;
                encoder.writeLong(rootComponentIdentifier.getInstanceId());
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

    private static void writeClassPathNotation(Encoder encoder, DependencyFactoryInternal.ClassPathNotation classPathNotation) throws IOException {
        encoder.writeSmallInt(classPathNotation.ordinal());
    }

    private static DependencyFactoryInternal.ClassPathNotation readClassPathNotation(Decoder decoder) throws IOException {
        int ordinal = decoder.readSmallInt();
        return DependencyFactoryInternal.ClassPathNotation.values()[ordinal];
    }

    private static Implementation resolveImplementation(ComponentIdentifier value) {
        if (value instanceof MavenUniqueSnapshotComponentIdentifier) {
            return Implementation.SNAPSHOT;
        } else if (value instanceof ModuleComponentIdentifier) {
            return Implementation.MODULE;
        } else if (value instanceof ProjectComponentIdentifierInternal) {
            return Implementation.PROJECT;
        } else if (value instanceof DefaultRootComponentIdentifier) {
            return Implementation.ROOT_COMPONENT;
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
        PROJECT(2),
        LIBRARY(6),
        SNAPSHOT(7),
        OPAQUE(8),
        OPAQUE_NOTATION(9),
        ROOT_COMPONENT(10);

        @Nullable
        public static Implementation valueOf(int id) {
            switch (id) {
                case 1: return MODULE;
                case 2: return PROJECT;
                case 6: return LIBRARY;
                case 7: return SNAPSHOT;
                case 8: return OPAQUE;
                case 9: return OPAQUE_NOTATION;
                case 10: return ROOT_COMPONENT;
            }
            return null;
        }

        private final byte id;

        Implementation(int id) {
            this.id = (byte) id;
        }
    }
}
