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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.internal.hash.HashValue;
import org.gradle.internal.serialize.*;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives;

import java.io.File;
import java.util.Set;

public class CompilationStateSerializer implements Serializer<CompilationState> {

    private static final int SERIAL_VERSION = 1;
    private final BaseSerializerFactory serializerFactory = new BaseSerializerFactory();
    private final Serializer<File> fileSerializer;
    private final ListSerializer<File> fileListSerializer;
    private final MapSerializer<File, CompilationFileState> stateMapSerializer;

    public CompilationStateSerializer() {
        fileSerializer = serializerFactory.getSerializerFor(File.class);
        fileListSerializer = new ListSerializer<File>(fileSerializer);
        stateMapSerializer = new MapSerializer<File, CompilationFileState>(fileSerializer, new CompilationFileStateSerializer());
    }

    @Override
    public CompilationState read(Decoder decoder) throws Exception {
        CompilationState compilationState = new CompilationState();
        int version = decoder.readInt();
        if (version != SERIAL_VERSION) {
            return compilationState;
        }

        compilationState.sourceInputs.addAll(fileListSerializer.read(decoder));
        compilationState.fileStates.putAll(stateMapSerializer.read(decoder));
        return compilationState;
    }

    @Override
    public void write(Encoder encoder, CompilationState value) throws Exception {
        encoder.writeInt(SERIAL_VERSION);
        fileListSerializer.write(encoder, value.sourceInputs);
        stateMapSerializer.write(encoder, value.fileStates);
    }

    private class CompilationFileStateSerializer implements Serializer<CompilationFileState> {
        private final Serializer<HashValue> hashSerializer = new HashValueSerializer();
        private final Serializer<Set<ResolvedInclude>> resolveIncludesSerializer = new SetSerializer<ResolvedInclude>(new ResolvedIncludeSerializer());
        private final Serializer<IncludeDirectives> sourceIncludesSerializer = new SourceIncludesSerializer();

        @Override
        public CompilationFileState read(Decoder decoder) throws Exception {
            CompilationFileState fileState = new CompilationFileState(hashSerializer.read(decoder));
            fileState.setResolvedIncludes(resolveIncludesSerializer.read(decoder));
            fileState.setIncludeDirectives(sourceIncludesSerializer.read(decoder));
            return fileState;
        }

        @Override
        public void write(Encoder encoder, CompilationFileState value) throws Exception {
            hashSerializer.write(encoder, value.getHash());
            resolveIncludesSerializer.write(encoder, value.getResolvedIncludes());
            sourceIncludesSerializer.write(encoder, value.getIncludeDirectives());
        }
    }

    private class ResolvedIncludeSerializer implements Serializer<ResolvedInclude> {
        @Override
        public ResolvedInclude read(Decoder decoder) throws Exception {
            String include = decoder.readString();
            File included = null;
            if (decoder.readBoolean()) {
                included = fileSerializer.read(decoder);
            }
            return new ResolvedInclude(include, included);
        }

        @Override
        public void write(Encoder encoder, ResolvedInclude value) throws Exception {
            encoder.writeString(value.getInclude());
            if (value.getFile() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                fileSerializer.write(encoder, value.getFile());
            }
        }
    }

    private class SourceIncludesSerializer implements Serializer<IncludeDirectives> {
        private final Serializer<Include> includeSerializer = new IncludeSerializer();
        private final ListSerializer<Include> includeListSerializer = new ListSerializer<Include>(includeSerializer);

        @Override
        public IncludeDirectives read(Decoder decoder) throws Exception {
            DefaultIncludeDirectives sourceIncludes = new DefaultIncludeDirectives();
            sourceIncludes.addAll(includeListSerializer.read(decoder));
            return sourceIncludes;
        }

        @Override
        public void write(Encoder encoder, IncludeDirectives value) throws Exception {
            includeListSerializer.write(encoder, value.getIncludesAndImports());
        }
    }

    private class IncludeSerializer implements Serializer<Include> {
        private final Serializer<String> stringSerializer = serializerFactory.getSerializerFor(String.class);
        private final Serializer<Boolean> booleanSerializer = serializerFactory.getSerializerFor(Boolean.class);
        private final Serializer<IncludeType> enumSerializer = serializerFactory.getSerializerFor(IncludeType.class);

        @Override
        public Include read(Decoder decoder) throws Exception {
            String value = stringSerializer.read(decoder);
            boolean isImport = booleanSerializer.read(decoder);
            IncludeType type = enumSerializer.read(decoder);
            return new DefaultInclude(value, isImport, type);
        }

        @Override
        public void write(Encoder encoder, Include value) throws Exception {
            stringSerializer.write(encoder, value.getValue());
            booleanSerializer.write(encoder, value.isImport());
            enumSerializer.write(encoder, value.getType());
        }
    }
}
