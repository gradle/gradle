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
package org.gradle.api.internal.tasks.compile.incremental.jar;

import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

class DefaultJarSnapshotter {
    private final FileHasher hasher;
    private final ClassDependenciesAnalyzer analyzer;

    public DefaultJarSnapshotter(FileHasher hasher, ClassDependenciesAnalyzer analyzer) {
        this.hasher = hasher;
        this.analyzer = analyzer;
    }

    public JarSnapshot createSnapshot(HashCode hash, JarArchive jarArchive) {
        final Map<String, HashCode> hashes = Maps.newHashMap();
        final ClassDependentsAccumulator accumulator = new ClassDependentsAccumulator();

        jarArchive.contents.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                if (!fileDetails.getName().endsWith(".class")) {
                    return;
                }

                HashCode classFileHash;
                InputStream inputStream = fileDetails.open();
                try {
                    classFileHash = hasher.hash(inputStream);
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                ClassAnalysis analysis = analyzer.getClassAnalysis(classFileHash, fileDetails);
                accumulator.addClass(analysis);

                hashes.put(analysis.getClassName(), classFileHash);
            }
        });

        return new JarSnapshot(new JarSnapshotData(hash, hashes, accumulator.getAnalysis()));
    }
}
