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
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CompilationStateSerializer implements Serializer<CompilationState> {
    private final Serializer<File> fileSerializer;
    private final Serializer<HashCode> hashSerializer = new HashCodeSerializer();

    public CompilationStateSerializer() {
        fileSerializer = new BaseSerializerFactory().getSerializerFor(File.class);
    }

    @Override
    public CompilationState read(Decoder decoder) throws Exception {
        // Deduplicates the include file states, as these are often shared between source files
        Map<Integer, IncludeFileState> ids = new HashMap<Integer, IncludeFileState>();
        int sourceFileCount = decoder.readSmallInt();
        ImmutableMap.Builder<File, SourceFileState> builder = ImmutableMap.builder();
        for (int i = 0; i < sourceFileCount; i++) {
            File sourceFile = fileSerializer.read(decoder);
            HashCode sourceHashCode = hashSerializer.read(decoder);
            int includeFileCount = decoder.readSmallInt();
            ImmutableSet.Builder<IncludeFileState> includeFileStateBuilder = ImmutableSet.builder();
            for (int j = 0; j < includeFileCount; j++) {
                int id = decoder.readSmallInt();
                IncludeFileState includeFileState = ids.get(id);
                if (includeFileState == null) {
                    File includeFile = fileSerializer.read(decoder);
                    HashCode includeHashCode = hashSerializer.read(decoder);
                    includeFileState = new IncludeFileState(includeHashCode, includeFile);
                    ids.put(id, includeFileState);
                }
                includeFileStateBuilder.add(includeFileState);
            }
            builder.put(sourceFile, new SourceFileState(sourceHashCode, includeFileStateBuilder.build()));
        }
        return new CompilationState(builder.build());
    }

    @Override
    public void write(Encoder encoder, CompilationState value) throws Exception {
        // Deduplicates the include file states, as these are often shared between source files
        Map<File, Integer> ids = new HashMap<File, Integer>();
        encoder.writeSmallInt(value.getFileStates().size());
        for (Map.Entry<File, SourceFileState> entry : value.getFileStates().entrySet()) {
            SourceFileState sourceFileState = entry.getValue();
            fileSerializer.write(encoder, entry.getKey());
            hashSerializer.write(encoder, sourceFileState.getHash());
            encoder.writeSmallInt(sourceFileState.getResolvedIncludes().size());
            for (IncludeFileState includeFileState : sourceFileState.getResolvedIncludes()) {
                Integer id = ids.get(includeFileState.getIncludeFile());
                if (id == null) {
                    id = ids.size();
                    ids.put(includeFileState.getIncludeFile(), id);
                    encoder.writeSmallInt(id);
                    fileSerializer.write(encoder, includeFileState.getIncludeFile());
                    hashSerializer.write(encoder, includeFileState.getHash());
                } else {
                    encoder.writeSmallInt(id);
                }
            }
        }
    }
}
