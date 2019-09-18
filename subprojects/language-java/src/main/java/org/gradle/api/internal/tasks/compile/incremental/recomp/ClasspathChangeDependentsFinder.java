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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassChanges;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.tasks.incremental.InputFileDetails;

import java.io.File;
import java.util.Deque;
import java.util.Set;

public class ClasspathChangeDependentsFinder {

    private final ClasspathSnapshot classpathSnapshot;
    private final PreviousCompilation previousCompilation;

    public ClasspathChangeDependentsFinder(ClasspathSnapshot classpathSnapshot, PreviousCompilation previousCompilation) {
        this.classpathSnapshot = classpathSnapshot;
        this.previousCompilation = previousCompilation;
    }

    public DependentsSet getActualDependents(InputFileDetails entryChangeDetails, File classpathEntry) {
        if (entryChangeDetails.isAdded()) {
            return handleAdded(classpathEntry);
        }

        ClasspathEntrySnapshot previous = previousCompilation.getClasspathEntrySnapshot(entryChangeDetails.getFile());

        if (previous == null) {
            return DependentsSet.dependencyToAll("missing classpath entry snapshot of '" + classpathEntry + "' from previous build");
        } else if (entryChangeDetails.isRemoved()) {
            return handleRemoved(previous);
        } else if (entryChangeDetails.isModified()) {
            return handleModified(classpathEntry, previous);
        } else {
            throw new IllegalArgumentException("Unknown input file details provided: " + entryChangeDetails);
        }
    }

    private DependentsSet handleAdded(File classpathEntry) {
        if (classpathSnapshot.isAnyClassDuplicated(classpathEntry)) {
            return DependentsSet.dependencyToAll("at least one of the classes of '" + classpathEntry + "' is already present in classpath");
        } else {
            return DependentsSet.empty();
        }
    }

    private DependentsSet handleRemoved(ClasspathEntrySnapshot previous) {
        DependentsSet allClasses = previous.getAllClasses();
        if (allClasses.isDependencyToAll()) {
            return allClasses;
        }
        DependentsSet affectedOnClasspath = collectDependentsFromClasspath(allClasses.getAllDependentClasses());
        if (affectedOnClasspath.isDependencyToAll()) {
            return affectedOnClasspath;
        } else {
            return previousCompilation.getDependents(affectedOnClasspath.getAllDependentClasses(), previous.getAllConstants(affectedOnClasspath));
        }
    }

    private DependentsSet handleModified(File classpathEntry, final ClasspathEntrySnapshot previous) {
        final ClasspathEntrySnapshot currentSnapshot = classpathSnapshot.getSnapshot(classpathEntry);
        ClassChanges classChanges = currentSnapshot.getChangedClassesSince(previous);

        if (classpathSnapshot.isAnyClassDuplicated(classChanges.getAdded())) {
            return DependentsSet.dependencyToAll("at least one of the classes of modified classpath entry '" + classpathEntry + "' is already present in the classpath");
        }

        DependentsSet affectedOnClasspath = collectDependentsFromClasspath(Sets.union(classChanges.getModified(), classChanges.getAdded()));
        if (affectedOnClasspath.isDependencyToAll()) {
            return affectedOnClasspath;
        } else {
            Set<String> joined = affectedOnClasspath.getAllDependentClasses();
            return previousCompilation.getDependents(joined, currentSnapshot.getRelevantConstants(previous, joined));
        }
    }

    private DependentsSet collectDependentsFromClasspath(Set<String> modified) {
        final Set<String> privateDependentClasses = Sets.newHashSet(modified);
        final Set<String> accessibleDependentClasses = Sets.newHashSet(modified);
        final Deque<String> queue = Lists.newLinkedList(modified);
        while (!queue.isEmpty()) {
            final String dependentClass = queue.poll();
            for (File entry : classpathSnapshot.getEntries()) {
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

    private DependentsSet collectDependentsFromClasspathEntry(String dependentClass, File entry) {
        ClasspathEntrySnapshot entrySnapshot = classpathSnapshot.getSnapshot(entry);
        ClassSetAnalysisData data = entrySnapshot.getData().getClassAnalysis();
        return data.getDependents(dependentClass);
    }

}
