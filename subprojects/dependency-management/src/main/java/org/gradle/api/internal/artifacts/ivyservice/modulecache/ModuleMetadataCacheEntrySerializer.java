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

import com.google.common.base.Objects;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.Encoder;

class ModuleMetadataCacheEntrySerializer extends AbstractSerializer<ModuleMetadataCacheEntry> {
    private final DefaultSerializer<ModuleSource> moduleSourceSerializer = new DefaultSerializer<ModuleSource>(ModuleSource.class.getClassLoader());

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
                moduleSourceSerializer.write(encoder, value.moduleSource);
                break;
            default:
                throw new IllegalArgumentException("Don't know how to serialize meta-data entry: " + value);
        }
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
                ModuleSource moduleSource = moduleSourceSerializer.read(decoder);
                return new ModuleMetadataCacheEntry(ModuleMetadataCacheEntry.TYPE_PRESENT, isChanging, createTimestamp, moduleSource);
            default:
                throw new IllegalArgumentException("Don't know how to deserialize meta-data entry of type " + type);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ModuleMetadataCacheEntrySerializer rhs = (ModuleMetadataCacheEntrySerializer) obj;
        return Objects.equal(moduleSourceSerializer, rhs.moduleSourceSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), moduleSourceSerializer);
    }
}
