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
package org.gradle.api.internal.artifacts.metadata;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

/**
 * A thread-safe and reusable serializer for {@link DefaultModuleComponentArtifactIdentifier}.
 */
public class ComponentArtifactIdentifierSerializer implements Serializer<DefaultModuleComponentArtifactIdentifier> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();

    @Override
    public void write(Encoder encoder, DefaultModuleComponentArtifactIdentifier value) throws Exception {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier());
        IvyArtifactNameSerializer.INSTANCE.write(encoder, value.getName());
    }

    @Override
    public DefaultModuleComponentArtifactIdentifier read(Decoder decoder) throws Exception {
        ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) componentIdentifierSerializer.read(decoder);
        IvyArtifactName name = IvyArtifactNameSerializer.INSTANCE.read(decoder);
        return new DefaultModuleComponentArtifactIdentifier(componentIdentifier, name);
    }
}
