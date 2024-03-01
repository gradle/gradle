/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.Cast;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@ServiceScope(Scopes.BuildTree.class)
public class ModuleSourcesSerializer implements Serializer<ModuleSources> {
    private final Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> moduleSourceCodecs;

    public ModuleSourcesSerializer(Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> moduleSourceCodecs) {
        this.moduleSourceCodecs = moduleSourceCodecs;
    }

    @Override
    public void write(Encoder encoder, ModuleSources value) throws IOException {
        value.withSources(source -> {
            try {
                if (source instanceof PersistentModuleSource) {
                    PersistentModuleSource persistentModuleSource = (PersistentModuleSource) source;
                    int codecId = assertValidId(persistentModuleSource.getCodecId());
                    encoder.writeSmallInt(codecId);
                    PersistentModuleSource.Codec<PersistentModuleSource> codec = Cast.uncheckedCast(moduleSourceCodecs.get(codecId));
                    codec.encode(persistentModuleSource, encoder);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        encoder.writeSmallInt(0); // end of sources
    }

    private int assertValidId(int codecId) {
        assert codecId >= 0 : "Module source must have a strictly positive source id";
        return codecId;
    }

    @Override
    public ModuleSources read(Decoder decoder) throws IOException {
        MutableModuleSources sources = new MutableModuleSources();
        int codecId;
        while ((codecId = decoder.readSmallInt()) > 0) {
            sources.add(moduleSourceCodecs.get(codecId).decode(decoder));
        }
        return sources;
    }
}
