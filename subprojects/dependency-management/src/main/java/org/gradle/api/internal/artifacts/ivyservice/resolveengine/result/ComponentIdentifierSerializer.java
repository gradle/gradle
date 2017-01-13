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
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;

public class ComponentIdentifierSerializer extends AbstractSerializer<ComponentIdentifier> {
    private final BuildIdentifierSerializer buildIdentifierSerializer = new BuildIdentifierSerializer();

    public ComponentIdentifier read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if (Implementation.BUILD.getId() == id) {
            BuildIdentifier buildIdentifier = buildIdentifierSerializer.read(decoder);
            return new DefaultProjectComponentIdentifier(buildIdentifier, decoder.readString());
        } else if (Implementation.MODULE.getId() == id) {
            return new DefaultModuleComponentIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
        } else if (Implementation.LIBRARY.getId() == id) {
            return new DefaultLibraryBinaryIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
        }

        throw new IllegalArgumentException("Unable to find component identifier type with id: " + id);
    }

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
        } else if (implementation == Implementation.BUILD) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) value;
            BuildIdentifier build = projectComponentIdentifier.getBuild();
            buildIdentifierSerializer.write(encoder, build);
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
        Implementation implementation;
        if (value instanceof ModuleComponentIdentifier) {
            implementation = Implementation.MODULE;
        } else if (value instanceof ProjectComponentIdentifier) {
            implementation = Implementation.BUILD;
        } else if (value instanceof LibraryBinaryIdentifier) {
            implementation = Implementation.LIBRARY;
        } else {
            throw new IllegalArgumentException("Unsupported component identifier class: " + value.getClass());
        }
        return implementation;
    }

    private enum Implementation {
        MODULE((byte) 1), BUILD((byte) 2), LIBRARY((byte) 3);

        private final byte id;

        Implementation(byte id) {
            this.id = id;
        }

        private byte getId() {
            return id;
        }
    }
}
