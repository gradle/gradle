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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.IOException;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

public class ModuleVersionSelectorSerializer implements Serializer<ModuleVersionSelector> {
    public ModuleVersionSelector read(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String name = decoder.readString();
        String version = decoder.readString();
        return newSelector(group, name, version);
    }

    public void write(Encoder encoder, ModuleVersionSelector value) throws IOException {
        encoder.writeString(value.getGroup());
        encoder.writeString(value.getName());
        encoder.writeString(value.getVersion());
    }
}
