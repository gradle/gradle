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
package org.gradle.api.internal.tasks.compile.incremental.classpath;

import com.google.common.collect.Maps;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.gradle.internal.FileUtils.hasExtension;

class DefaultClasspathEntrySnapshotter {
    private static final Logger LOGGER = Logging.getLogger(DefaultClasspathEntrySnapshotter.class);

    private final StreamHasher hasher;
    private final ClassDependenciesAnalyzer analyzer;
    private final FileOperations fileOperations;

    public DefaultClasspathEntrySnapshotter(StreamHasher hasher, ClassDependenciesAnalyzer analyzer, FileOperations fileOperations) {
        this.hasher = hasher;
        this.analyzer = analyzer;
        this.fileOperations = fileOperations;
    }

    public ClasspathEntrySnapshot createSnapshot(HashCode hash, File classpathEntry) {
        final Map<String, HashCode> hashes = Maps.newHashMap();
        final ClassDependentsAccumulator accumulator = new ClassDependentsAccumulator();

        try {
            FileTree contents = getContents(classpathEntry);
            if (contents != null) {
                contents.visit(new EntryVisitor(accumulator, hashes));
            }
        } catch (Exception e) {
            accumulator.fullRebuildNeeded("classpath entry" + classpathEntry + " could not be analyzed. See the debug log for more details");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not analyze classpath entry " + classpathEntry, e);
            }
        }

        return new ClasspathEntrySnapshot(new ClasspathEntrySnapshotData(hash, hashes, accumulator.getAnalysis()));
    }

    private FileTree getContents(File classpathEntry) {
        if (hasExtension(classpathEntry, ".jar")) {
            return fileOperations.zipTree(classpathEntry);
        }
        if (classpathEntry.isDirectory()) {
            return fileOperations.fileTree(classpathEntry);
        }
        return null;

    }

    private class EntryVisitor implements FileVisitor {
        private final ClassDependentsAccumulator accumulator;
        private final Map<String, HashCode> hashes;

        public EntryVisitor(ClassDependentsAccumulator accumulator, Map<String, HashCode> hashes) {
            this.accumulator = accumulator;
            this.hashes = hashes;
        }

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

            try {
                ClassAnalysis analysis = analyzer.getClassAnalysis(classFileHash, fileDetails);
                accumulator.addClass(analysis);
                hashes.put(analysis.getClassName(), classFileHash);
            } catch (Exception e) {
                accumulator.fullRebuildNeeded("class file " + fileDetails.getName() + " could not be analyzed. See the debug log for more details");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Could not analyze class file " + fileDetails.getName(), e);
                }
            }
        }
    }
}
