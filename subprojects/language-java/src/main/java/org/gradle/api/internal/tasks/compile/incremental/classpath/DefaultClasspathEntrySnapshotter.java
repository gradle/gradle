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
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;
import org.gradle.internal.IoActions;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import static org.gradle.internal.FileUtils.hasExtension;

public class DefaultClasspathEntrySnapshotter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClasspathEntrySnapshotter.class);

    private final FileHasher fileHasher;
    private final StreamHasher hasher;
    private final ClassDependenciesAnalyzer analyzer;
    private final FileOperations fileOperations;

    public DefaultClasspathEntrySnapshotter(FileHasher fileHasher, StreamHasher streamHasher, ClassDependenciesAnalyzer analyzer, FileOperations fileOperations) {
        this.fileHasher = fileHasher;
        this.hasher = streamHasher;
        this.analyzer = analyzer;
        this.fileOperations = fileOperations;
    }

    public ClasspathEntrySnapshot createSnapshot(HashCode hash, File classpathEntry) {
        final Map<String, HashCode> hashes = Maps.newHashMap();
        final ClassDependentsAccumulator accumulator = new ClassDependentsAccumulator();

        try {
            visit(classpathEntry, hashes, accumulator);
        } catch (Exception e) {
            accumulator.fullRebuildNeeded(classpathEntry + " could not be analyzed for incremental compilation. See the debug log for more details");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not analyze " + classpathEntry + " for incremental compilation", e);
            }
        }

        return new ClasspathEntrySnapshot(new ClasspathEntrySnapshotData(hash, hashes, accumulator.getAnalysis()));
    }

    private void visit(File classpathEntry, Map<String, HashCode> hashes, ClassDependentsAccumulator accumulator) {
        if (hasExtension(classpathEntry, ".jar")) {
            fileOperations.zipTree(classpathEntry).visit(new JarEntryVisitor(accumulator, hashes));
        }
        if (classpathEntry.isDirectory()) {
            fileOperations.fileTree(classpathEntry).visit(new DirectoryEntryVisitor(accumulator, hashes));
        }
    }

    private abstract class EntryVisitor implements FileVisitor {
        private final ClassDependentsAccumulator accumulator;
        private final Map<String, HashCode> hashes;

        public EntryVisitor(ClassDependentsAccumulator accumulator, Map<String, HashCode> hashes) {
            this.accumulator = accumulator;
            this.hashes = hashes;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            if (!fileDetails.getName().endsWith(".class")) {
                return;
            }

            HashCode classFileHash = getHashCode(fileDetails);

            try {
                ClassAnalysis analysis = analyzer.getClassAnalysis(classFileHash, fileDetails);
                accumulator.addClass(analysis);
                hashes.put(analysis.getClassName(), classFileHash);
            } catch (Exception e) {
                accumulator.fullRebuildNeeded(fileDetails.getName() + " could not be analyzed for incremental compilation. See the debug log for more details");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Could not analyze " + fileDetails.getName() + " for incremental compilation", e);
                }
            }
        }

        protected abstract HashCode getHashCode(FileVisitDetails fileDetails);
    }

    private class JarEntryVisitor extends EntryVisitor {

        public JarEntryVisitor(ClassDependentsAccumulator accumulator, Map<String, HashCode> hashes) {
            super(accumulator, hashes);
        }

        @Override
        protected HashCode getHashCode(FileVisitDetails fileDetails) {
            InputStream inputStream = fileDetails.open();
            try {
                return hasher.hash(inputStream);
            } finally {
                IoActions.closeQuietly(inputStream);
            }
        }
    }

    private class DirectoryEntryVisitor extends EntryVisitor {

        public DirectoryEntryVisitor(ClassDependentsAccumulator accumulator, Map<String, HashCode> hashes) {
            super(accumulator, hashes);
        }

        @Override
        protected HashCode getHashCode(FileVisitDetails fileDetails) {
            return fileHasher.hash(fileDetails);
        }
    }

}
