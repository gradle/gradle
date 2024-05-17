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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.util.function.Supplier;

public class PreviousCompilationData {
    private final ClassSetAnalysisData outputSnapshot;
    private final AnnotationProcessingData annotationProcessingData;
    private final ClassSetAnalysisData classpathSnapshot;
    private final CompilerApiData compilerApiData;

    public PreviousCompilationData(ClassSetAnalysisData outputSnapshot, AnnotationProcessingData annotationProcessingData, ClassSetAnalysisData classpathSnapshot, CompilerApiData compilerApiData) {
        this.outputSnapshot = outputSnapshot;
        this.annotationProcessingData = annotationProcessingData;
        this.classpathSnapshot = classpathSnapshot;
        this.compilerApiData = compilerApiData;
    }

    public ClassSetAnalysisData getOutputSnapshot() {
        return outputSnapshot;
    }

    public AnnotationProcessingData getAnnotationProcessingData() {
        return annotationProcessingData;
    }

    public ClassSetAnalysisData getClasspathSnapshot() {
        return classpathSnapshot;
    }

    public CompilerApiData getCompilerApiData() {
        return compilerApiData;
    }

    public static class Serializer extends AbstractSerializer<PreviousCompilationData> {

        private final StringInterner interner;

        public Serializer(StringInterner interner) {
            this.interner = interner;
        }

        @Override
        public PreviousCompilationData read(Decoder decoder) throws Exception {
            HierarchicalNameSerializer hierarchicalNameSerializer = new HierarchicalNameSerializer(interner);
            Supplier<HierarchicalNameSerializer> classNameSerializerSupplier = () -> hierarchicalNameSerializer;
            ClassSetAnalysisData.Serializer analysisSerializer = new ClassSetAnalysisData.Serializer(classNameSerializerSupplier);
            AnnotationProcessingData.Serializer annotationProcessingDataSerializer = new AnnotationProcessingData.Serializer(classNameSerializerSupplier);
            CompilerApiData.Serializer compilerApiDataSerializer = new CompilerApiData.Serializer(classNameSerializerSupplier);

            ClassSetAnalysisData outputSnapshot = analysisSerializer.read(decoder);
            AnnotationProcessingData annotationProcessingData = annotationProcessingDataSerializer.read(decoder);
            ClassSetAnalysisData classpathSnapshot = analysisSerializer.read(decoder);
            CompilerApiData compilerApiData = compilerApiDataSerializer.read(decoder);
            return new PreviousCompilationData(outputSnapshot, annotationProcessingData, classpathSnapshot, compilerApiData);
        }

        @Override
        public void write(Encoder encoder, PreviousCompilationData value) throws Exception {
            HierarchicalNameSerializer hierarchicalNameSerializer = new HierarchicalNameSerializer(interner);
            Supplier<HierarchicalNameSerializer> classNameSerializerSupplier = () -> hierarchicalNameSerializer;
            ClassSetAnalysisData.Serializer analysisSerializer = new ClassSetAnalysisData.Serializer(classNameSerializerSupplier);
            AnnotationProcessingData.Serializer annotationProcessingDataSerializer = new AnnotationProcessingData.Serializer(classNameSerializerSupplier);
            CompilerApiData.Serializer compilerApiDataSerializer = new CompilerApiData.Serializer(classNameSerializerSupplier);

            analysisSerializer.write(encoder, value.outputSnapshot);
            annotationProcessingDataSerializer.write(encoder, value.annotationProcessingData);
            analysisSerializer.write(encoder, value.classpathSnapshot);
            compilerApiDataSerializer.write(encoder, value.compilerApiData);
        }
    }
}
