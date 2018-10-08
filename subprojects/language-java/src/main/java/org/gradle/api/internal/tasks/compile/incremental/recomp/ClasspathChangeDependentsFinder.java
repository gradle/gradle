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
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.deps.AffectedClasses;
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
            if (classpathSnapshot.isAnyClassDuplicated(classpathEntry)) {
                //at least one of the classes from the new entry is already present in classpath
                //to avoid calculation which class gets on the classpath first, rebuild all
                return DependentsSet.dependencyToAll("at least one of the classes of '" + classpathEntry + "' is already present in classpath");
            } else {
                //none of the new classes in the entry are duplicated on classpath, don't rebuild
                return DependentsSet.empty();
            }
        }
        final ClasspathEntrySnapshot previous = previousCompilation.getClasspathEntrySnapshot(entryChangeDetails.getFile());

        if (previous == null) {
            //we don't know what classes were dependents of the entry in the previous build
            //for example, a class with a constant might have changed into a class without a constant - we need to rebuild everything
            return DependentsSet.dependencyToAll("missing classpath entry snapshot of '" + classpathEntry + "' from previous build");
        }

        if (entryChangeDetails.isRemoved()) {
            DependentsSet allClasses = previous.getAllClasses();
            if (allClasses.isDependencyToAll()) {
                return allClasses;
            }
            //recompile all dependents of all the classes from this entry
            return previousCompilation.getDependents(allClasses.getDependentClasses(), previous.getAllConstants(allClasses));
        }

        if (entryChangeDetails.isModified()) {
            final ClasspathEntrySnapshot currentSnapshot = classpathSnapshot.getSnapshot(classpathEntry);
            AffectedClasses affected = currentSnapshot.getAffectedClassesSince(previous);
            DependentsSet altered = affected.getAltered();
            if (altered.isDependencyToAll()) {
                //at least one of the classes changed in the entry is a 'dependency-to-all'
                return altered;
            }

            if (classpathSnapshot.isAnyClassDuplicated(affected.getAdded())) {
                //A new duplicate class on classpath. As we don't fancy-handle classpath order right now, we don't know which class is on classpath first.
                //For safe measure rebuild everything
                return DependentsSet.dependencyToAll("at least one of the classes of modified classpath entry '" + classpathEntry + "' is already present in the classpath");
            }

            //recompile all dependents of the classes changed in the entry

            final Set<String> dependentClasses = Sets.newHashSet(altered.getDependentClasses());
            final Deque<String> queue = Lists.newLinkedList(dependentClasses);
            while (!queue.isEmpty()) {
                final String dependentClass = queue.poll();
                classpathSnapshot.forEachSnapshot(new Action<ClasspathEntrySnapshot>() {
                    @Override
                    public void execute(ClasspathEntrySnapshot classpathEntrySnapshot) {
                        if (classpathEntrySnapshot != previous) {
                            // we need to find classes in other entries that would potentially extend classes changed
                            // in the current snapshot (they are intermediates)
                            ClassSetAnalysisData data = classpathEntrySnapshot.getData().getClassAnalysis();
                            Set<String> children = data.getChildren(dependentClass);
                            for (String child : children) {
                                if (dependentClasses.add(child)) {
                                    queue.add(child);
                                }
                            }
                        }
                    }
                });
            }
            return previousCompilation.getDependents(dependentClasses, currentSnapshot.getRelevantConstants(previous, dependentClasses));
        }

        throw new IllegalArgumentException("Unknown input file details provided: " + entryChangeDetails);
    }
}
