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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.List;

public class CompilationStateSerializer implements Serializer<CompilationState> {
    private final MapSerializer<File, SourceFileState> stateMapSerializer;

    public CompilationStateSerializer() {
        Serializer<File> fileSerializer = new BaseSerializerFactory().getSerializerFor(File.class);
        stateMapSerializer = new MapSerializer<File, SourceFileState>(fileSerializer, new CompilationFileStateSerializer(fileSerializer));
    }

    @Override
    public CompilationState read(Decoder decoder) throws Exception {
        ImmutableMap<File, SourceFileState> fileStates = ImmutableMap.copyOf(stateMapSerializer.read(decoder));
        return new CompilationState(fileStates);
    }

    @Override
    public void write(Encoder encoder, CompilationState value) throws Exception {
        stateMapSerializer.write(encoder, value.getFileStates());
    }

    private static class CompilationFileStateSerializer implements Serializer<SourceFileState> {
        private final Serializer<HashCode> hashSerializer = new HashCodeSerializer();
        private final Serializer<List<IncludeFileState>> resolveIncludesSerializer;

        private CompilationFileStateSerializer(final Serializer<File> fileSerializer) {
            this.resolveIncludesSerializer = new ListSerializer<IncludeFileState>(new IncludeFileStateSerializer(hashSerializer, fileSerializer));
        }

        @Override
        public SourceFileState read(Decoder decoder) throws Exception {
            HashCode hash = hashSerializer.read(decoder);
            ImmutableList<IncludeFileState> resolvedIncludes = ImmutableList.copyOf(resolveIncludesSerializer.read(decoder));
            return new SourceFileState(hash, resolvedIncludes);
        }

        @Override
        public void write(Encoder encoder, SourceFileState value) throws Exception {
            hashSerializer.write(encoder, value.getHash());
            resolveIncludesSerializer.write(encoder, value.getResolvedIncludes());
        }

    }

    private static class IncludeFileStateSerializer implements Serializer<IncludeFileState> {
        private final Serializer<HashCode> hashSerializer;
        private final Serializer<File> fileSerializer;

        public IncludeFileStateSerializer(Serializer<HashCode> hashSerializer, Serializer<File> fileSerializer) {
            this.hashSerializer = hashSerializer;
            this.fileSerializer = fileSerializer;
        }

        @Override
        public IncludeFileState read(Decoder decoder) throws Exception {
            HashCode hashCode = hashSerializer.read(decoder);
            File file = fileSerializer.read(decoder);
            return new IncludeFileState(hashCode, file);
        }

        @Override
        public void write(Encoder encoder, IncludeFileState value) throws Exception {
            hashSerializer.write(encoder, value.getHash());
            fileSerializer.write(encoder, value.getIncludeFile());
        }
    }
}
