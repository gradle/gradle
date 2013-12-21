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

import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.component.DefaultProjectComponentSelector;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentSelector;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.IOException;

public class ComponentSelectorSerializer implements Serializer<ComponentSelector> {
    public ComponentSelector read(Decoder decoder) throws IOException {
        byte id = decoder.readByte();

        if(Implementation.BUILD.getId() == id) {
            return new DefaultProjectComponentSelector(decoder.readString());
        } else if(Implementation.MODULE.getId() == id) {
            return new DefaultModuleComponentSelector(decoder.readString(), decoder.readString(), decoder.readString());
        }

        throw new IllegalArgumentException("Unable to find component selector with id: " + id);
    }

    public void write(Encoder encoder, ComponentSelector value) throws IOException {
        if(value == null) {
            throw new IllegalArgumentException("Provided component selector may not be null");
        }

        if(value instanceof DefaultModuleComponentSelector) {
            ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector)value;
            encoder.writeByte(Implementation.MODULE.getId());
            encoder.writeString(moduleComponentSelector.getGroup());
            encoder.writeString(moduleComponentSelector.getModule());
            encoder.writeString(moduleComponentSelector.getVersion());
        } else if(value instanceof DefaultProjectComponentSelector) {
            ProjectComponentSelector projectComponentSelector = (ProjectComponentSelector)value;
            encoder.writeByte(Implementation.BUILD.getId());
            encoder.writeString(projectComponentSelector.getProjectPath());
        } else {
            throw new IllegalArgumentException("Unsupported component selector class: " + value.getClass());
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
