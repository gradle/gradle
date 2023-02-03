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
        Map<Integer, IncludeFileEdge> ids = new HashMap<Integer, IncludeFileEdge>();
        int sourceFileCount = decoder.readSmallInt();
        ImmutableMap.Builder<File, SourceFileState> builder = ImmutableMap.builder();
        for (int i = 0; i < sourceFileCount; i++) {
            File sourceFile = fileSerializer.read(decoder);
            HashCode sourceHashCode = hashSerializer.read(decoder);
            boolean isUnresolved = decoder.readBoolean();
            int includeFileCount = decoder.readSmallInt();
            ImmutableSet.Builder<IncludeFileEdge> includeFileStateBuilder = ImmutableSet.builder();
            for (int j = 0; j < includeFileCount; j++) {
                int id = decoder.readSmallInt();
                IncludeFileEdge includeFileState = ids.get(id);
                if (includeFileState == null) {
                    String includePath = decoder.readString();
                    HashCode includedBy = null;
                    if (decoder.readBoolean()) {
                        includedBy = hashSerializer.read(decoder);
                    }
                    HashCode resolvedTo = hashSerializer.read(decoder);
                    includeFileState = new IncludeFileEdge(includePath, includedBy, resolvedTo);
                    ids.put(id, includeFileState);
                }
                includeFileStateBuilder.add(includeFileState);
            }
            builder.put(sourceFile, new SourceFileState(sourceHashCode, isUnresolved, includeFileStateBuilder.build()));
        }
        return new CompilationState(builder.build());
    }

    @Override
    public void write(Encoder encoder, CompilationState value) throws Exception {
        // Deduplicates the include file states, as these are often shared between source files
        Map<IncludeFileEdge, Integer> ids = new HashMap<IncludeFileEdge, Integer>();
        encoder.writeSmallInt(value.getFileStates().size());
        for (Map.Entry<File, SourceFileState> entry : value.getFileStates().entrySet()) {
            SourceFileState sourceFileState = entry.getValue();
            fileSerializer.write(encoder, entry.getKey());
            hashSerializer.write(encoder, sourceFileState.getHash());
            encoder.writeBoolean(sourceFileState.isHasUnresolved());
            encoder.writeSmallInt(sourceFileState.getEdges().size());
            for (IncludeFileEdge includeFileState : sourceFileState.getEdges()) {
                Integer id = ids.get(includeFileState);
                if (id == null) {
                    id = ids.size();
                    ids.put(includeFileState, id);
                    encoder.writeSmallInt(id);
                    encoder.writeString(includeFileState.getIncludePath());
                    if (includeFileState.getIncludedBy() == null) {
                        encoder.writeBoolean(false);
                    } else {
                        encoder.writeBoolean(true);
                        hashSerializer.write(encoder, includeFileState.getIncludedBy());
                    }
                    hashSerializer.write(encoder, includeFileState.getResolvedTo());
                } else {
                    encoder.writeSmallInt(id);
                }
            }
        }
    }
}
