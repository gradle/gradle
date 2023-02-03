/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link ModuleVersionIdentifier} if and only if the passed in
 * {@link ImmutableModuleIdentifierFactory} is itself thread-safe.
 */
public class ModuleVersionIdentifierSerializer implements Serializer<ModuleVersionIdentifier> {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public ModuleVersionIdentifierSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @Override
    public void write(Encoder encoder, ModuleVersionIdentifier value) throws IOException {
        encoder.writeString(value.getGroup());
        encoder.writeString(value.getName());
        encoder.writeString(value.getVersion());
    }

    @Override
    public ModuleVersionIdentifier read(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String module = decoder.readString();
        String version = decoder.readString();
        return moduleIdentifierFactory.moduleWithVersion(group, module, version);
    }
}
