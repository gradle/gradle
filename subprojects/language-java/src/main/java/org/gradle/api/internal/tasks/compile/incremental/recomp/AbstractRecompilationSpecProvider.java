/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.execution.history.changes.DefaultFileChange;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy;
import org.gradle.internal.util.Alignment;
import org.gradle.language.base.internal.tasks.StaleOutputCleaner;
import org.gradle.work.FileChange;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

abstract class AbstractRecompilationSpecProvider implements RecompilationSpecProvider {
    private final Deleter deleter;
    protected final FileOperations fileOperations;
    protected final FileTree sourceTree;

    public AbstractRecompilationSpecProvider(
        Deleter deleter,
        FileOperations fileOperations,
        FileTree sourceTree
    ) {
        this.deleter = deleter;
        this.fileOperations = fileOperations;
        this.sourceTree = sourceTree;
    }

    protected boolean deleteStaleFilesIn(PatternSet filesToDelete, final File destinationDir) {
        if (filesToDelete == null || filesToDelete.isEmpty() || destinationDir == null) {
            return false;
        }
        Set<File> toDelete = fileOperations.fileTree(destinationDir).matching(filesToDelete).getFiles();
        return StaleOutputCleaner.cleanOutputs(deleter, toDelete, destinationDir);
    }

    protected void processClasspathChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        ClasspathEntryChangeProcessor classpathEntryChangeProcessor = new ClasspathEntryChangeProcessor(current.getClasspathSnapshot(), previous);
        ClasspathSnapshot currentSnapshots = current.getClasspathSnapshot();

        Set<File> previousCompilationEntries = previous.getClasspath();
        Set<File> currentCompilationEntries = currentSnapshots.getEntries();
        List<Alignment<File>> alignment = Alignment.align(currentCompilationEntries.toArray(new File[0]), previousCompilationEntries.toArray(new File[0]));
        for (Alignment<File> fileAlignment : alignment) {
            switch (fileAlignment.getKind()) {
                case added:
                    DefaultFileChange added = DefaultFileChange.added(fileAlignment.getCurrentValue().getAbsolutePath(), "classpathEntry", FileType.RegularFile, IgnoredPathFingerprintingStrategy.IGNORED_PATH);
                    classpathEntryChangeProcessor.processChange(added, spec);
                    break;
                case removed:
                    DefaultFileChange removed = DefaultFileChange.removed(fileAlignment.getPreviousValue().getAbsolutePath(), "classpathEntry", FileType.RegularFile, IgnoredPathFingerprintingStrategy.IGNORED_PATH);
                    classpathEntryChangeProcessor.processChange(removed, spec);
                    break;
                case transformed:
                    // If we detect a transformation in the classpath, we need to recompile, because we could typically be facing the case where
                    // 2 entries are reversed in the order of classpath elements, and one class that was shadowing the other is now visible
                    spec.setFullRebuildCause("Classpath has been changed", null);
                    return;
                case identical:
                    File key = fileAlignment.getPreviousValue();
                    ClasspathEntrySnapshot previousSnapshot = previous.getClasspathEntrySnapshot(key);
                    ClasspathEntrySnapshot snapshot = currentSnapshots.getSnapshot(key);
                    if (previousSnapshot == null || !snapshot.getHash().equals(previousSnapshot.getHash())) {
                        DefaultFileChange modified = DefaultFileChange.modified(key.getAbsolutePath(), "classpathEntry", FileType.RegularFile, FileType.RegularFile, IgnoredPathFingerprintingStrategy.IGNORED_PATH);
                        classpathEntryChangeProcessor.processChange(modified, spec);
                    }
                    break;
            }
        }
    }

    protected void addClassesToProcess(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        Set<String> classesToProcess = Sets.newHashSet(recompilationSpec.getClassesToProcess());
        classesToProcess.removeAll(recompilationSpec.getClassesToCompile());
        spec.setClasses(classesToProcess);
    }

    protected void includePreviousCompilationOutputOnClasspath(JavaCompileSpec spec) {
        List<File> classpath = Lists.newArrayList(spec.getCompileClasspath());
        File destinationDir = spec.getDestinationDir();
        classpath.add(destinationDir);
        spec.setCompileClasspath(classpath);
    }

    protected String rebuildClauseForChangedNonSourceFile(String description, FileChange fileChange) {
        return String.format("%s '%s' has been %s", description, fileChange.getFile().getName(), fileChange.getChangeType().name().toLowerCase(Locale.US));
    }
}
