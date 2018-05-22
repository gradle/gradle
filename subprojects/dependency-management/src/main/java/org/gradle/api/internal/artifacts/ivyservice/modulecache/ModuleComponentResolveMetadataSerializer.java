/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.EOFException;

public class ModuleComponentResolveMetadataSerializer extends AbstractSerializer<ModuleComponentResolveMetadata> {

    private final ModuleMetadataSerializer delegate;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public ModuleComponentResolveMetadataSerializer(ModuleMetadataSerializer delegate, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.delegate = delegate;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @Override
    public ModuleComponentResolveMetadata read(Decoder decoder) throws EOFException, Exception {

        return delegate.read(decoder, moduleIdentifierFactory).asImmutable();
    }

    @Override
    public void write(Encoder encoder, ModuleComponentResolveMetadata value) throws Exception {
        delegate.write(encoder, value);
    }
}
