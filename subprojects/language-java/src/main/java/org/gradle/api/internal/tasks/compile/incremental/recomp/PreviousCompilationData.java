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

import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotDataSerializer;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;

import java.io.File;
import java.util.List;

public class PreviousCompilationData {
    private final File destinationDir;
    private final AnnotationProcessingData annotationProcessingData;
    private final ClasspathSnapshotData classpathSnapshot;
    private final List<File> annotationProcessorPath;

    public PreviousCompilationData(File destinationDir, AnnotationProcessingData annotationProcessingData, ClasspathSnapshotData classpathSnapshot, List<File> annotationProcessorPath) {
        this.destinationDir = destinationDir;
        this.annotationProcessingData = annotationProcessingData;
        this.classpathSnapshot = classpathSnapshot;
        this.annotationProcessorPath = annotationProcessorPath;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public AnnotationProcessingData getAnnotationProcessingData() {
        return annotationProcessingData;
    }

    public ClasspathSnapshotData getClasspathSnapshot() {
        return classpathSnapshot;
    }

    public List<File> getAnnotationProcessorPath() {
        return annotationProcessorPath;
    }

    public static class Serializer extends AbstractSerializer<PreviousCompilationData> {
        private static final ClasspathSnapshotDataSerializer CLASSPATH_SNAPSHOT_SERIALIZER = new ClasspathSnapshotDataSerializer();
        private static final ListSerializer<File> PROCESSOR_PATH_SERIALZER = new ListSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER);
        private static final AnnotationProcessingData.Serializer ANNOTATION_PROCESSING_DATA_SERIALIZER = new AnnotationProcessingData.Serializer();

        @Override
        public PreviousCompilationData read(Decoder decoder) throws Exception {
            File destinationDir = BaseSerializerFactory.FILE_SERIALIZER.read(decoder);
            ClasspathSnapshotData classpathSnapshot = CLASSPATH_SNAPSHOT_SERIALIZER.read(decoder);
            List<File> processorPath = PROCESSOR_PATH_SERIALZER.read(decoder);
            AnnotationProcessingData annotationProcessingData = ANNOTATION_PROCESSING_DATA_SERIALIZER.read(decoder);
            return new PreviousCompilationData(destinationDir, annotationProcessingData, classpathSnapshot, processorPath);
        }

        @Override
        public void write(Encoder encoder, PreviousCompilationData value) throws Exception {
            BaseSerializerFactory.FILE_SERIALIZER.write(encoder, value.destinationDir);
            CLASSPATH_SNAPSHOT_SERIALIZER.write(encoder, value.classpathSnapshot);
            PROCESSOR_PATH_SERIALZER.write(encoder, value.annotationProcessorPath);
            ANNOTATION_PROCESSING_DATA_SERIALIZER.write(encoder, value.annotationProcessingData);
        }
    }
}
