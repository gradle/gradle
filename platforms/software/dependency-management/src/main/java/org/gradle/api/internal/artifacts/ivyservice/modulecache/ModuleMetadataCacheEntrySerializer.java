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

import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

class ModuleMetadataCacheEntrySerializer extends AbstractSerializer<ModuleMetadataCacheEntry> {

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
                return new ModuleMetadataCacheEntry(ModuleMetadataCacheEntry.TYPE_PRESENT, isChanging, createTimestamp);
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
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
