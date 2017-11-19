/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;

public class IncludeDirectivesSerializer implements Serializer<IncludeDirectives> {
    private final ListSerializer<Include> includeListSerializer = new ListSerializer<Include>(new IncludeSerializer());
    private final ListSerializer<Macro> macroListSerializer = new ListSerializer<Macro>(new MacroSerializer());

    @Override
    public IncludeDirectives read(Decoder decoder) throws Exception {
        return new DefaultIncludeDirectives(ImmutableList.copyOf(includeListSerializer.read(decoder)), ImmutableList.copyOf(macroListSerializer.read(decoder)));
    }

    @Override
    public void write(Encoder encoder, IncludeDirectives value) throws Exception {
        includeListSerializer.write(encoder, value.getAll());
        macroListSerializer.write(encoder, value.getMacros());
    }

    private static class IncludeSerializer implements Serializer<Include> {
        private final Serializer<IncludeType> enumSerializer = new BaseSerializerFactory().getSerializerFor(IncludeType.class);

        @Override
        public Include read(Decoder decoder) throws Exception {
            String value = decoder.readString();
            boolean isImport = decoder.readBoolean();
            IncludeType type = enumSerializer.read(decoder);
            return DefaultInclude.create(value, isImport, type);
        }

        @Override
        public void write(Encoder encoder, Include value) throws Exception {
            encoder.writeString(value.getValue());
            encoder.writeBoolean(value.isImport());
            enumSerializer.write(encoder, value.getType());
        }
    }

    private static class MacroSerializer implements Serializer<Macro> {
        private static final byte RESOLVED = (byte) 1;
        private static final byte UNRESOLVED = (byte) 2;

        @Override
        public Macro read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == RESOLVED) {
                String name = decoder.readString();
                String value = decoder.readString();
                return new DefaultMacro(name, value);
            } else if (tag == UNRESOLVED) {
                String name = decoder.readString();
                return new UnresolveableMacro(name);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void write(Encoder encoder, Macro value) throws Exception {
            if (value instanceof DefaultMacro) {
                encoder.writeByte(RESOLVED);
                encoder.writeString(value.getName());
                encoder.writeString(value.getValue());
            } else if (value instanceof UnresolveableMacro) {
                encoder.writeByte(UNRESOLVED);
                encoder.writeString(value.getName());
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }
}
