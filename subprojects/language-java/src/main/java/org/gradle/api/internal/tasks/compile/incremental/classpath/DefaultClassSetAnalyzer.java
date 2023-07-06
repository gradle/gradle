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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.IoActions;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

import static org.gradle.internal.FileUtils.hasExtension;

public class DefaultClassSetAnalyzer implements ClassSetAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClassSetAnalyzer.class);

    private final FileHasher fileHasher;
    private final StreamHasher hasher;
    private final ClassDependenciesAnalyzer analyzer;
    private final FileOperations fileOperations;

    public DefaultClassSetAnalyzer(FileHasher fileHasher, StreamHasher streamHasher, ClassDependenciesAnalyzer analyzer, FileOperations fileOperations) {
        this.fileHasher = fileHasher;
        this.hasher = streamHasher;
        this.analyzer = analyzer;
        this.fileOperations = fileOperations;
    }

    public ClassSetAnalysisData analyzeClasspathEntry(File classpathEntry) {
        return analyze(classpathEntry, true);
    }

    @Override
    public ClassSetAnalysisData analyzeOutputFolder(File outputFolder) {
        return analyze(outputFolder, false);
    }

    private ClassSetAnalysisData analyze(File classSet, boolean abiOnly) {
        final ClassDependentsAccumulator accumulator = new ClassDependentsAccumulator();
        final Timer clock = Time.startTimer();
        try {
            visit(classSet, accumulator, abiOnly);
        } catch (Exception e) {
            accumulator.fullRebuildNeeded(classSet + " could not be analyzed for incremental compilation. See the debug log for more details");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not analyze " + classSet + " for incremental compilation", e);
            }
        }

        LOGGER.info(Thread.currentThread().getName() + " only visit for " + classSet + " took " + clock.getElapsed());
        ClassSetAnalysisData analysis = accumulator.getAnalysis();
        LOGGER.info(Thread.currentThread().getName() + " overall analysis for " + classSet + " took " + clock.getElapsed());
        return analysis;
    }

    private void visit(File classpathEntry, ClassDependentsAccumulator accumulator, boolean abiOnly) {
        if (hasExtension(classpathEntry, ".jar")) {
            fileOperations.zipTreeNoLocking(classpathEntry).visit(new JarEntryVisitor(accumulator, abiOnly));
        }
        if (classpathEntry.isDirectory()) {
            fileOperations.fileTree(classpathEntry).visit(new DirectoryEntryVisitor(accumulator, abiOnly));
        }
    }

    private abstract class EntryVisitor implements FileVisitor {
        private final ClassDependentsAccumulator accumulator;
        private final boolean abiOnly;

        public EntryVisitor(ClassDependentsAccumulator accumulator, boolean abiOnly) {
            this.accumulator = accumulator;
            this.abiOnly = abiOnly;
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
                ClassAnalysis analysis = maybeStripToAbi(analyzer.getClassAnalysis(classFileHash, fileDetails));
                accumulator.addClass(analysis, classFileHash);
            } catch (Exception e) {
                accumulator.fullRebuildNeeded(fileDetails.getName() + " could not be analyzed for incremental compilation. See the debug log for more details");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Could not analyze " + fileDetails.getName() + " for incremental compilation", e);
                }
            }
        }

        private ClassAnalysis maybeStripToAbi(ClassAnalysis analysis) {
            if (abiOnly) {
                return new ClassAnalysis(analysis.getClassName(), ImmutableSet.of(), analysis.getAccessibleClassDependencies(), analysis.getDependencyToAllReason(), analysis.getConstants());
            } else {
                return analysis;
            }
        }

        protected abstract HashCode getHashCode(FileVisitDetails fileDetails);
    }

    private class JarEntryVisitor extends EntryVisitor {

        public JarEntryVisitor(ClassDependentsAccumulator accumulator, boolean abiOnly) {
            super(accumulator, abiOnly);
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

        public DirectoryEntryVisitor(ClassDependentsAccumulator accumulator, boolean abiOnly) {
            super(accumulator, abiOnly);
        }

        @Override
        protected HashCode getHashCode(FileVisitDetails fileDetails) {
            return fileHasher.hash(fileDetails.getFile(), fileDetails.getSize(), fileDetails.getLastModified());
        }
    }

}
