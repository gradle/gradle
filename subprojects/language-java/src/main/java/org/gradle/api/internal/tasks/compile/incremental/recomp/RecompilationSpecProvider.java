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

import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gradle.internal.change.FileChange;
import org.gradle.internal.file.FileType;
import org.gradle.internal.util.Alignment;

import java.io.File;
import java.util.List;
import java.util.Set;

public class RecompilationSpecProvider {

    private final SourceToNameConverter sourceToNameConverter;

    public RecompilationSpecProvider(SourceToNameConverter sourceToNameConverter) {
        this.sourceToNameConverter = sourceToNameConverter;
    }

    public RecompilationSpec provideRecompilationSpec(CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec spec = new RecompilationSpec();
        processClasspathChanges(current, previous, spec);
        processOtherChanges(current, previous, spec);
        spec.getClassesToProcess().addAll(previous.getTypesToReprocess());
        return spec;
    }

    private void processClasspathChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        ClasspathEntryChangeProcessor classpathEntryChangeProcessor = new ClasspathEntryChangeProcessor(current.getClasspathSnapshot(), previous);
        ClasspathSnapshot currentSnapshots = current.getClasspathSnapshot();

        Set<File> previousCompilationEntries = previous.getClasspath();
        Set<File> currentCompilationEntries = currentSnapshots.getEntries();
        List<Alignment<File>> alignment = Alignment.align(currentCompilationEntries.toArray(new File[0]), previousCompilationEntries.toArray(new File[0]));
        for (Alignment<File> fileAlignment : alignment) {
            switch (fileAlignment.getKind()) {
                case added:
                    classpathEntryChangeProcessor.processChange(FileChange.added(fileAlignment.getCurrentValue().getAbsolutePath(), "classpathEntry", FileType.RegularFile), spec);
                    break;
                case removed:
                    classpathEntryChangeProcessor.processChange(FileChange.removed(fileAlignment.getPreviousValue().getAbsolutePath(), "classpathEntry", FileType.RegularFile), spec);
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
                        classpathEntryChangeProcessor.processChange(FileChange.modified(key.getAbsolutePath(), "classpathEntry", FileType.RegularFile, FileType.RegularFile), spec);
                    }
                    break;
            }
        }
    }

    private void processOtherChanges(CurrentCompilation current, PreviousCompilation previous, RecompilationSpec spec) {
        JavaChangeProcessor javaChangeProcessor = new JavaChangeProcessor(previous, sourceToNameConverter);
        AnnotationProcessorChangeProcessor annotationProcessorChangeProcessor = new AnnotationProcessorChangeProcessor(current, previous);
        ResourceChangeProcessor resourceChangeProcessor = new ResourceChangeProcessor(current.getAnnotationProcessorPath());
        InputChangeAction action = new InputChangeAction(spec, javaChangeProcessor, annotationProcessorChangeProcessor, resourceChangeProcessor);
        current.visitChanges(action);
    }
}
