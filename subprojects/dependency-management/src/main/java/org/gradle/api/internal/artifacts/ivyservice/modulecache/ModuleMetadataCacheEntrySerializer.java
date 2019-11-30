/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

class ModuleMetadataCacheEntrySerializer extends AbstractSerializer<ModuleMetadataCacheEntry> {
    private final Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> moduleSourceCodecs;

    public ModuleMetadataCacheEntrySerializer(Map<Integer, PersistentModuleSource.Codec<? extends PersistentModuleSource>> codecs) {
        this.moduleSourceCodecs = codecs;
    }

    @Override
    public void write(Encoder encoder, ModuleMetadataCacheEntry value) throws Exception {
        encoder.writeByte(value.type);
        switch (value.type) {
            case ModuleMetadataCacheEntry.TYPE_MISSING:
                encoder.writeLong(value.createTimestamp);
                break;
            case ModuleMetadataCacheEntry.TYPE_PRESENT:
                encoder.writeBoolean(value.isChanging);
                encoder.writeLong(value.createTimestamp);
                writeModuleSources(encoder, value);
                break;
            default:
                throw new IllegalArgumentException("Don't know how to serialize meta-data entry: " + value);
        }
    }

    private void writeModuleSources(Encoder encoder, ModuleMetadataCacheEntry value) throws IOException {
        value.moduleSources.withSources(source -> {
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
    public ModuleMetadataCacheEntry read(Decoder decoder) throws Exception {
        byte type = decoder.readByte();
        switch (type) {
            case ModuleMetadataCacheEntry.TYPE_MISSING:
                long createTimestamp = decoder.readLong();
                return new MissingModuleCacheEntry(createTimestamp);
            case ModuleMetadataCacheEntry.TYPE_PRESENT:
                boolean isChanging = decoder.readBoolean();
                createTimestamp = decoder.readLong();
                MutableModuleSources sources = new MutableModuleSources();
                int codecId;
                while ((codecId=decoder.readSmallInt())>0) {
                    sources.add(moduleSourceCodecs.get(codecId).decode(decoder));
                }
                return new ModuleMetadataCacheEntry(ModuleMetadataCacheEntry.TYPE_PRESENT, isChanging, createTimestamp, sources);
            default:
                throw new IllegalArgumentException("Don't know how to deserialize meta-data entry of type " + type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ModuleMetadataCacheEntrySerializer that = (ModuleMetadataCacheEntrySerializer) o;

        return moduleSourceCodecs.equals(that.moduleSourceCodecs);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + moduleSourceCodecs.hashCode();
        return result;
    }
}
