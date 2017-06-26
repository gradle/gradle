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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives;

import java.io.File;
import java.util.Set;

public class CompilationStateSerializer extends AbstractSerializer<CompilationState> {
    private static final BaseSerializerFactory SERIALIZER_FACTORY = new BaseSerializerFactory();
    private final Serializer<File> fileSerializer;
    private final SetSerializer<File> fileSetSerializer;
    private final MapSerializer<File, CompilationFileState> stateMapSerializer;

    public CompilationStateSerializer() {
        fileSerializer = SERIALIZER_FACTORY.getSerializerFor(File.class);
        fileSetSerializer = new SetSerializer<File>(fileSerializer);
        stateMapSerializer = new MapSerializer<File, CompilationFileState>(fileSerializer,
            new CompilationFileStateSerializer(fileSerializer));
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

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        CompilationStateSerializer rhs = (CompilationStateSerializer) obj;
        return Objects.equal(fileSerializer, rhs.fileSerializer)
            && Objects.equal(fileSetSerializer, rhs.fileSetSerializer)
            && Objects.equal(stateMapSerializer, rhs.stateMapSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), fileSerializer, fileSetSerializer, stateMapSerializer);
    }

    private static class CompilationFileStateSerializer extends AbstractSerializer<CompilationFileState> {
        private final Serializer<HashCode> hashSerializer = new HashCodeSerializer();
        private final Serializer<Set<ResolvedInclude>> resolveIncludesSerializer;
        private final Serializer<IncludeDirectives> sourceIncludesSerializer = new SourceIncludesSerializer();

        private CompilationFileStateSerializer(Serializer<File> fileSerializer) {
            this.resolveIncludesSerializer = new SetSerializer<ResolvedInclude>(new ResolvedIncludeSerializer(fileSerializer));
        }

        @Override
        public CompilationFileState read(Decoder decoder) throws Exception {
            HashCode hash = hashSerializer.read(decoder);
            ImmutableSet<ResolvedInclude> resolvedIncludes = ImmutableSet.copyOf(resolveIncludesSerializer.read(decoder));
            IncludeDirectives includeDirectives = sourceIncludesSerializer.read(decoder);
            return new CompilationFileState(hash, includeDirectives, resolvedIncludes);
        }

        @Override
        public void write(Encoder encoder, CompilationFileState value) throws Exception {
            hashSerializer.write(encoder, value.getHash());
            resolveIncludesSerializer.write(encoder, value.getResolvedIncludes());
            sourceIncludesSerializer.write(encoder, value.getIncludeDirectives());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            CompilationFileStateSerializer rhs = (CompilationFileStateSerializer) obj;
            return Objects.equal(hashSerializer, rhs.hashSerializer)
                && Objects.equal(resolveIncludesSerializer, rhs.resolveIncludesSerializer)
                && Objects.equal(sourceIncludesSerializer, rhs.sourceIncludesSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), hashSerializer, resolveIncludesSerializer, sourceIncludesSerializer);
        }
    }

    private static class ResolvedIncludeSerializer extends AbstractSerializer<ResolvedInclude> {
        private final Serializer<File> fileSerializer;

        private ResolvedIncludeSerializer(Serializer<File> fileSerializer) {
            this.fileSerializer = fileSerializer;
        }

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

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            ResolvedIncludeSerializer rhs = (ResolvedIncludeSerializer) obj;
            return Objects.equal(fileSerializer, rhs.fileSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), fileSerializer);
        }
    }

    private static class SourceIncludesSerializer extends AbstractSerializer<IncludeDirectives> {
        private final Serializer<Include> includeSerializer = new IncludeSerializer();
        private final ListSerializer<Include> includeListSerializer = new ListSerializer<Include>(includeSerializer);

        @Override
        public IncludeDirectives read(Decoder decoder) throws Exception {
            return new DefaultIncludeDirectives(includeListSerializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, IncludeDirectives value) throws Exception {
            includeListSerializer.write(encoder, value.getIncludesAndImports());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            SourceIncludesSerializer rhs = (SourceIncludesSerializer) obj;
            return Objects.equal(includeSerializer, rhs.includeSerializer)
                && Objects.equal(includeListSerializer, rhs.includeListSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), includeSerializer, includeListSerializer);
        }
    }

    private static class IncludeSerializer extends AbstractSerializer<Include> {
        private final Serializer<String> stringSerializer = SERIALIZER_FACTORY.getSerializerFor(String.class);
        private final Serializer<Boolean> booleanSerializer = SERIALIZER_FACTORY.getSerializerFor(Boolean.class);
        private final Serializer<IncludeType> enumSerializer = SERIALIZER_FACTORY.getSerializerFor(IncludeType.class);

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

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            IncludeSerializer rhs = (IncludeSerializer) obj;
            return Objects.equal(stringSerializer, rhs.stringSerializer)
                && Objects.equal(booleanSerializer, rhs.booleanSerializer)
                && Objects.equal(enumSerializer, rhs.enumSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), stringSerializer, booleanSerializer, enumSerializer);
        }
    }
}
