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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.util.Map;

/**
 * Defines the JSON protocol for plugin resolution service responses to plugin metadata queries.
 */
public class PluginUseMetaData {

    public static final String M2_JAR = "M2_JAR";

    public final String id;
    public final String version;
    public final Map<String, String> implementation;
    public final String implementationType;
    public final boolean legacy;

    public PluginUseMetaData(String id, String version, Map<String, String> implementation, String implementationType, boolean legacy) {
        this.id = id;
        this.version = version;
        this.implementation = implementation;
        this.implementationType = implementationType;
        this.legacy = legacy;
        if (!legacy) {
            throw new RuntimeException("GOT A NON LEGACY: " + this);
        }
    }

    @Override
    public String toString() {
        return "PluginUseMetaData{"
            + "id='" + id + '\''
            + ", version='" + version + '\''
            + ", implementation=" + implementation
            + ", implementationType='" + implementationType + '\''
            + ", legacy=" + legacy
            + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginUseMetaData)) {
            return false;
        }

        PluginUseMetaData that = (PluginUseMetaData) o;

        if (legacy != that.legacy) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        if (!implementation.equals(that.implementation)) {
            return false;
        }
        if (!implementationType.equals(that.implementationType)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + implementation.hashCode();
        result = 31 * result + implementationType.hashCode();
        result = 31 * result + (legacy ? 1 : 0);
        return result;
    }

    public static class Serializer extends AbstractSerializer<PluginUseMetaData> {
        public PluginUseMetaData read(Decoder decoder) throws Exception {
            return new PluginUseMetaData(
                decoder.readString(),
                decoder.readString(),
                BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER.read(decoder),
                decoder.readString(),
                decoder.readBoolean()
            );
        }

        public void write(Encoder encoder, PluginUseMetaData value) throws Exception {
            encoder.writeString(value.id);
            encoder.writeString(value.version);
            BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER.write(encoder, value.implementation);
            encoder.writeString(value.implementationType);
            encoder.writeBoolean(value.legacy);
        }
    }
}
