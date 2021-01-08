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
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

//TODO reuse cached result from downstream users of our classes directory
public class PreviousCompilationOutputAnalyzer {
    private static final Logger LOG = LoggerFactory.getLogger(PreviousCompilationOutputAnalyzer.class);

    private final DefaultClasspathEntrySnapshotter snapshotter;

    public PreviousCompilationOutputAnalyzer(FileHasher fileHasher, StreamHasher streamHasher, ClassDependenciesAnalyzer analyzer, FileOperations fileOperations) {
        this.snapshotter = new DefaultClasspathEntrySnapshotter(fileHasher, streamHasher, analyzer, fileOperations);
    }

    public ClassSetAnalysis getAnalysis(File classesDirectory) {
        Timer clock = Time.startTimer();
        HashCode unusedHashCode = HashCode.fromInt(0);
        ClasspathEntrySnapshot snapshot = snapshotter.createSnapshot(unusedHashCode, classesDirectory);
        LOG.info("Class dependency analysis for incremental compilation took {}.", clock.getElapsed());
        return snapshot.getClassAnalysis();
    }

}
