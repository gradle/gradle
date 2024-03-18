/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize;

import com.google.common.base.CharMatcher;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Interner;

import java.io.IOException;

/**
 * Efficiently serializes hierarchical names, like Java class names or relative paths of resources.
 * Splits names into prefixes and suffixes along package separators, inner class separators, file separators and camel case borders.
 * Reuses these prefixes and suffixes to efficiently store names or parts of names it has seen before.
 *
 * This class is stateful. Use a new one for each serialization/deserialization attempt.
 */
public class HierarchicalNameSerializer extends AbstractSerializer<String> {
    private static final CharMatcher SEPARATOR_MATCHER = CharMatcher.anyOf(".$/").or(CharMatcher.inRange('A', 'Z'));

    private final Interner<String> interner;
    private final BiMap<Integer, String> namesById = HashBiMap.create();

    public HierarchicalNameSerializer(Interner<String> interner) {
        this.interner = interner;
    }

    @Override
    public String read(Decoder decoder) throws Exception {
        String name = readName(decoder);
        return interner.intern(name);
    }

    @Override
    public void write(Encoder encoder, String name) throws Exception {
        writeName(name, encoder);
    }

    private String readName(Decoder decoder) throws IOException {
        int id = decoder.readSmallInt();
        String name = namesById.get(id);
        if (name == null) {
            name = readFirstOccurrenceOfName(decoder);
            namesById.put(id, name);
        }
        return name;
    }

    private String readFirstOccurrenceOfName(Decoder decoder) throws IOException {
        byte separator = decoder.readByte();
        if (separator == 0) {
            return decoder.readString();
        } else {
            String parent = readName(decoder);
            String child = readName(decoder);
            return parent + (char) (separator & 0xFF) + child;
        }
    }

    private void writeName(String name, Encoder encoder) throws IOException {
        Integer id = namesById.inverse().get(name);
        if (id == null) {
            id = namesById.inverse().size();
            namesById.inverse().put(name, id);
            encoder.writeSmallInt(id);
            writeFirstOccurrenceOfName(name, encoder);
        } else {
            encoder.writeSmallInt(id);
        }
    }

    private void writeFirstOccurrenceOfName(String name, Encoder encoder) throws IOException {
        int separator = SEPARATOR_MATCHER.lastIndexIn(name);
        if (separator > 0) {
            String parent = name.substring(0, separator);
            String child = name.substring(separator + 1);
            encoder.writeByte((byte) name.charAt(separator));
            writeName(parent, encoder);
            writeName(child, encoder);
        } else {
            encoder.writeByte((byte) 0);
            encoder.writeString(name);
        }
    }
}
