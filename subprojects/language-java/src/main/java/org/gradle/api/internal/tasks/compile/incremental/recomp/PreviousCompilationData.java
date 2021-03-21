/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.util.List;

public class PreviousCompilationData {
    private final AnnotationProcessingData annotationProcessingData;
    private final List<HashCode> classpathHashes;

    public PreviousCompilationData(AnnotationProcessingData annotationProcessingData, List<HashCode> classpathHashes) {
        this.annotationProcessingData = annotationProcessingData;
        this.classpathHashes = classpathHashes;
    }

    public AnnotationProcessingData getAnnotationProcessingData() {
        return annotationProcessingData;
    }

    public List<HashCode> getClasspathHashes() {
        return classpathHashes;
    }

    public static class Serializer extends AbstractSerializer<PreviousCompilationData> {
        private final ListSerializer<HashCode> hashSerializer = new ListSerializer<>(new HashCodeSerializer());
        private final AnnotationProcessingData.Serializer annotationProcessingDataSerializer = new AnnotationProcessingData.Serializer();

        @Override
        public PreviousCompilationData read(Decoder decoder) throws Exception {
            List<HashCode> classpathHashes = hashSerializer.read(decoder);
            AnnotationProcessingData annotationProcessingData = annotationProcessingDataSerializer.read(decoder);
            return new PreviousCompilationData(annotationProcessingData, classpathHashes);
        }

        @Override
        public void write(Encoder encoder, PreviousCompilationData value) throws Exception {
            hashSerializer.write(encoder, value.classpathHashes);
            annotationProcessingDataSerializer.write(encoder, value.annotationProcessingData);
        }
    }
}
