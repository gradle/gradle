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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.IOException;

public class ComponentIdentifierSerializer implements Serializer<ComponentIdentifier> {
    public ComponentIdentifier read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if(Implementation.BUILD.getId() == id) {
            return new DefaultProjectComponentIdentifier(decoder.readString());
        } else if(Implementation.MODULE.getId() == id) {
            return new DefaultModuleComponentIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
        }

        throw new IllegalArgumentException("Unable to find component identifier with id: " + id);
    }

    public void write(Encoder encoder, ComponentIdentifier value) throws IOException {
        if(value == null) {
            throw new IllegalArgumentException("Provided component identifier may not be null");
        }

        if(value instanceof DefaultModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)value;
            encoder.writeByte(Implementation.MODULE.getId());
            encoder.writeString(moduleComponentIdentifier.getGroup());
            encoder.writeString(moduleComponentIdentifier.getModule());
            encoder.writeString(moduleComponentIdentifier.getVersion());
        } else if(value instanceof DefaultProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier)value;
            encoder.writeByte(Implementation.BUILD.getId());
            encoder.writeString(projectComponentIdentifier.getProjectPath());
        } else {
            throw new IllegalArgumentException("Unsupported component identifier class: " + value.getClass());
        }
    }

    private static enum Implementation {
        MODULE((byte) 1), BUILD((byte) 2);

        private final byte id;

        private Implementation(byte id) {
            this.id = id;
        }

        private byte getId() {
            return id;
        }
    }
}
