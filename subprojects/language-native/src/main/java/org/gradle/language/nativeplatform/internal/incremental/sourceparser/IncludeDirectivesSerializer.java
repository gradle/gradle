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
import org.gradle.language.nativeplatform.internal.MacroFunction;

public class IncludeDirectivesSerializer implements Serializer<IncludeDirectives> {
    private final Serializer<IncludeType> enumSerializer = new BaseSerializerFactory().getSerializerFor(IncludeType.class);
    private final ListSerializer<Include> includeListSerializer = new ListSerializer<Include>(new IncludeSerializer(enumSerializer));
    private final ListSerializer<Macro> macroListSerializer = new ListSerializer<Macro>(new MacroSerializer(enumSerializer));
    private final ListSerializer<MacroFunction> macroFunctionListSerializer = new ListSerializer<MacroFunction>(new MacroFunctionSerializer(enumSerializer));

    @Override
    public IncludeDirectives read(Decoder decoder) throws Exception {
        return new DefaultIncludeDirectives(ImmutableList.copyOf(includeListSerializer.read(decoder)), ImmutableList.copyOf(macroListSerializer.read(decoder)), ImmutableList.copyOf(macroFunctionListSerializer.read(decoder)));
    }

    @Override
    public void write(Encoder encoder, IncludeDirectives value) throws Exception {
        includeListSerializer.write(encoder, value.getAll());
        macroListSerializer.write(encoder, value.getMacros());
        macroFunctionListSerializer.write(encoder, value.getMacrosFunctions());
    }

    private static class IncludeSerializer implements Serializer<Include> {
        private final Serializer<IncludeType> enumSerializer;

        private IncludeSerializer(Serializer<IncludeType> enumSerializer) {
            this.enumSerializer = enumSerializer;
        }

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
        private final Serializer<IncludeType> enumSerializer;

        MacroSerializer(Serializer<IncludeType> enumSerializer) {
            this.enumSerializer = enumSerializer;
        }

        @Override
        public Macro read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == RESOLVED) {
                String name = decoder.readString();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readString();
                return new DefaultMacro(name, type, value);
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
                enumSerializer.write(encoder, value.getType());
                encoder.writeString(value.getValue());
            } else if (value instanceof UnresolveableMacro) {
                encoder.writeByte(UNRESOLVED);
                encoder.writeString(value.getName());
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    private static class MacroFunctionSerializer implements Serializer<MacroFunction> {
        private static final byte RESOLVED = (byte) 1;
        private static final byte UNRESOLVED = (byte) 2;
        private final Serializer<IncludeType> enumSerializer;

        MacroFunctionSerializer(Serializer<IncludeType> enumSerializer) {
            this.enumSerializer = enumSerializer;
        }

        @Override
        public MacroFunction read(Decoder decoder) throws Exception {
            byte tag = decoder.readByte();
            if (tag == RESOLVED) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                IncludeType type = enumSerializer.read(decoder);
                String value = decoder.readString();
                return new DefaultMacroFunction(name, parameters, type, value);
            } else if (tag == UNRESOLVED) {
                String name = decoder.readString();
                int parameters = decoder.readSmallInt();
                return new UnresolveableMacroFunction(name, parameters);
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void write(Encoder encoder, MacroFunction value) throws Exception {
            if (value instanceof DefaultMacroFunction) {
                encoder.writeByte(RESOLVED);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
                enumSerializer.write(encoder, value.getType());
                encoder.writeString(value.getValue());
            } else if (value instanceof UnresolveableMacroFunction) {
                encoder.writeByte(UNRESOLVED);
                encoder.writeString(value.getName());
                encoder.writeSmallInt(value.getParameterCount());
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }
}
