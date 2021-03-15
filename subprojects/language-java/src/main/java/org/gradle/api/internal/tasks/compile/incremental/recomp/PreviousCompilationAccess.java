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

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.DefaultClasspathEntrySnapshotter;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class PreviousCompilationAccess {
    private static final Logger LOG = LoggerFactory.getLogger(PreviousCompilationAccess.class);

    private final DefaultClasspathEntrySnapshotter snapshotter;

    public PreviousCompilationAccess(FileHasher fileHasher, StreamHasher streamHasher, ClassDependenciesAnalyzer analyzer, FileOperations fileOperations) {
        this.snapshotter = new DefaultClasspathEntrySnapshotter(fileHasher, streamHasher, analyzer, fileOperations);
    }

    public ClassSetAnalysis analyseClasses(File classesDirectory) {
        Timer clock = Time.startTimer();
        HashCode unusedHashCode = HashCode.fromInt(0);
        ClasspathEntrySnapshot snapshot = snapshotter.createSnapshot(unusedHashCode, classesDirectory);
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
        return snapshot.getClassAnalysis();
    }

    public PreviousCompilationData readPreviousCompilationData(File source) {
        try (KryoBackedDecoder encoder = new KryoBackedDecoder(new FileInputStream(source))) {
            return new PreviousCompilationData.Serializer().read(encoder);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read previous compilation result.", e);
        }
    }

    public void writePreviousCompilationData(PreviousCompilationData data, File target) {
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(new FileOutputStream(target))) {
            new PreviousCompilationData.Serializer().write(encoder, data);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store compilation result", e);
        }
    }
}
