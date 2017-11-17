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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.IncludeDirectivesSerializer;

import java.io.File;
import java.util.Set;

public class CompilationStateSerializer implements Serializer<CompilationState> {
    private final SetSerializer<File> fileSetSerializer;
    private final MapSerializer<File, CompilationFileState> stateMapSerializer;

    public CompilationStateSerializer() {
        Serializer<File> fileSerializer = new BaseSerializerFactory().getSerializerFor(File.class);
        fileSetSerializer = new SetSerializer<File>(fileSerializer);
        stateMapSerializer = new MapSerializer<File, CompilationFileState>(fileSerializer, new CompilationFileStateSerializer(fileSerializer));
    }

    @Override
    public CompilationState read(Decoder decoder) throws Exception {
        ImmutableSet<File> sourceInputs = ImmutableSet.copyOf(fileSetSerializer.read(decoder));
        ImmutableMap<File, CompilationFileState> fileStates = ImmutableMap.copyOf(stateMapSerializer.read(decoder));
        return new CompilationState(sourceInputs, fileStates);
    }

    @Override
    public void write(Encoder encoder, CompilationState value) throws Exception {
        fileSetSerializer.write(encoder, value.getSourceInputs());
        stateMapSerializer.write(encoder, value.getFileStates());
    }

    private static class CompilationFileStateSerializer implements Serializer<CompilationFileState> {
        private final Serializer<HashCode> hashSerializer = new HashCodeSerializer();
        private final Serializer<Set<File>> resolveIncludesSerializer;
        private final Serializer<IncludeDirectives> sourceIncludesSerializer = new IncludeDirectivesSerializer();

        private CompilationFileStateSerializer(Serializer<File> fileSerializer) {
            this.resolveIncludesSerializer = new SetSerializer<File>(fileSerializer);
        }

        @Override
        public CompilationFileState read(Decoder decoder) throws Exception {
            HashCode hash = hashSerializer.read(decoder);
            ImmutableSet<File> resolvedIncludes = ImmutableSet.copyOf(resolveIncludesSerializer.read(decoder));
            IncludeDirectives includeDirectives = sourceIncludesSerializer.read(decoder);
            return new CompilationFileState(hash, includeDirectives, resolvedIncludes);
        }

        @Override
        public void write(Encoder encoder, CompilationFileState value) throws Exception {
            hashSerializer.write(encoder, value.getHash());
            resolveIncludesSerializer.write(encoder, value.getResolvedIncludes());
            sourceIncludesSerializer.write(encoder, value.getIncludeDirectives());
        }
    }
}
