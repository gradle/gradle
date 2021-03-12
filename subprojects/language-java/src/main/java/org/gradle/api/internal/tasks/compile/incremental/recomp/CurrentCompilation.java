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

import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotProvider;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.File;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CurrentCompilation {
    private final JavaCompileSpec spec;
    private final ClasspathSnapshotProvider classpathSnapshotProvider;

    public CurrentCompilation(JavaCompileSpec spec, ClasspathSnapshotProvider classpathSnapshotProvider) {
        this.spec = spec;
        this.classpathSnapshotProvider = classpathSnapshotProvider;
    }

    public Collection<File> getAnnotationProcessorPath() {
        return spec.getAnnotationProcessorPath();
    }

    public void processChangesSince(PreviousCompilation previous, RecompilationSpec spec) {
        List<ClasspathEntrySnapshot> currentClasspath = getClasspath();
        List<ClasspathEntrySnapshot> previousClasspath = previous.getClasspath();
        int commonSize = Math.min(currentClasspath.size(), previousClasspath.size());
        for (int i = 0; i < commonSize; i++) {
            ClasspathEntrySnapshot currentEntry = currentClasspath.get(i);
            ClasspathEntrySnapshot olderEntry = previousClasspath.get(i);
            DependentsSet changes = handleModified(currentEntry, olderEntry, previous);
            applyChanges(changes, spec);
        }
        List<ClasspathEntrySnapshot> longer = currentClasspath.size() > commonSize ? currentClasspath : previousClasspath;
        List<ClasspathEntrySnapshot> rest = longer.subList(commonSize, longer.size());
        for (ClasspathEntrySnapshot snapshot : rest) {
            DependentsSet changes = handleAddedOrRemoved(snapshot, previous);
            applyChanges(changes, spec);
        }
    }

    private List<ClasspathEntrySnapshot> getClasspath() {
        return classpathSnapshotProvider.getClasspathSnapshot(Iterables.concat(this.spec.getCompileClasspath(), this.spec.getModulePath())).getEntries();
    }

    private DependentsSet handleModified(ClasspathEntrySnapshot current, ClasspathEntrySnapshot older, PreviousCompilation previous) {
        DependentsSet affectedOnClasspath = collectDependentsFromClasspath(current.getChangedClassesSince(older), previous);
        if (affectedOnClasspath.isDependencyToAll()) {
            return affectedOnClasspath;
        } else {
            Set<String> joined = affectedOnClasspath.getAllDependentClasses();
            return previous.getDependents(joined, current.getRelevantConstants(older, joined));
        }
    }

    private DependentsSet handleAddedOrRemoved(ClasspathEntrySnapshot entry, PreviousCompilation previous) {
        DependentsSet allClasses = entry.getAllClasses();
        if (allClasses.isDependencyToAll()) {
            return allClasses;
        }
        DependentsSet affectedOnClasspath = collectDependentsFromClasspath(allClasses.getAllDependentClasses(), previous);
        if (affectedOnClasspath.isDependencyToAll()) {
            return affectedOnClasspath;
        } else {
            return previous.getDependents(affectedOnClasspath.getAllDependentClasses(), entry.getAllConstants(affectedOnClasspath));
        }
    }

    private DependentsSet collectDependentsFromClasspath(Set<String> modified, PreviousCompilation older) {
        final Set<String> privateDependentClasses = new HashSet<>(modified);
        final Set<String> accessibleDependentClasses = new HashSet<>(modified);
        final Deque<String> queue = new LinkedList<>(modified);
        while (!queue.isEmpty()) {
            final String dependentClass = queue.poll();
            for (ClasspathEntrySnapshot entry : older.getClasspath()) {
                DependentsSet dependents = collectDependentsFromClasspathEntry(dependentClass, entry);
                if (dependents.isDependencyToAll()) {
                    return dependents;
                } else {
                    for (String intermediate : dependents.getPrivateDependentClasses()) {
                        if (privateDependentClasses.add(intermediate) && !accessibleDependentClasses.contains(intermediate)) {
                            queue.add(intermediate);
                        }
                    }
                    for (String intermediate : dependents.getAccessibleDependentClasses()) {
                        if (accessibleDependentClasses.add(intermediate) && !privateDependentClasses.contains(intermediate)) {
                            queue.add(intermediate);
                        }
                    }
                }
            }
        }
        return DependentsSet.dependentClasses(privateDependentClasses, accessibleDependentClasses);
    }

    private DependentsSet collectDependentsFromClasspathEntry(String dependentClass, ClasspathEntrySnapshot entry) {
        ClassSetAnalysisData data = entry.getData().getClassAnalysis();
        return data.getDependents(dependentClass);
    }

    private void applyChanges(DependentsSet changes, RecompilationSpec spec) {
        if (changes.isDependencyToAll()) {
            String description = changes.getDescription();
            spec.setFullRebuildCause(description != null ? description : "TODO better message", null);
            return;
        }
        spec.addClassesToCompile(changes.getPrivateDependentClasses());
        spec.addClassesToCompile(changes.getAccessibleDependentClasses());
        spec.addResourcesToGenerate(changes.getDependentResources());
    }
}
