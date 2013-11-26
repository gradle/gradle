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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.api.artifacts.component.BuildComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultBuildComponentIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.IOException;

public class ComponentIdentifierSerializer implements Serializer<ComponentIdentifier> {
    private static final BiMap<Integer, Class> IMPLEMENTATIONS = HashBiMap.create(2);

    static {
        IMPLEMENTATIONS.put(1, DefaultModuleComponentIdentifier.class);
        IMPLEMENTATIONS.put(2, DefaultBuildComponentIdentifier.class);
    }

    public ComponentIdentifier read(Decoder decoder) throws IOException {
        int id = decoder.readInt();
        Class componentIdClass = IMPLEMENTATIONS.get(id);

        if(componentIdClass == null) {
            throw new IllegalArgumentException("Unable to find component identifier with id: " + id);
        }

        if(componentIdClass == DefaultBuildComponentIdentifier.class) {
            return new DefaultBuildComponentIdentifier(decoder.readString());
        }

        return new DefaultModuleComponentIdentifier(decoder.readString(), decoder.readString(), decoder.readString());
    }

    public void write(Encoder encoder, ComponentIdentifier value) throws IOException {
        if(value == null) {
            throw new IllegalArgumentException("Provided component identifier may not be null");
        }

        if(!IMPLEMENTATIONS.containsValue(value.getClass())) {
            throw new IllegalArgumentException("Unsupported component identifier class: " + value.getClass());
        }

        int id = IMPLEMENTATIONS.inverse().get(value.getClass());
        encoder.writeInt(id);

        if(value instanceof DefaultModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)value;
            encoder.writeString(moduleComponentIdentifier.getGroup());
            encoder.writeString(moduleComponentIdentifier.getModule());
            encoder.writeString(moduleComponentIdentifier.getVersion());
        } else if(value instanceof DefaultBuildComponentIdentifier) {
            BuildComponentIdentifier buildComponentIdentifier = (BuildComponentIdentifier)value;
            encoder.writeString(buildComponentIdentifier.getProjectPath());
        }
    }
}
