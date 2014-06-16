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

import org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.model.PreviousCompilation;
import org.gradle.api.tasks.incremental.InputFileDetails;

public class JarChangeDependentsFinder {

    private DefaultJarSnapshotter jarSnapshotter;
    private PreviousCompilation previousCompilation;

    public JarChangeDependentsFinder(DefaultJarSnapshotter jarSnapshotter, PreviousCompilation previousCompilation) {
        this.jarSnapshotter = jarSnapshotter;
        this.previousCompilation = previousCompilation;
    }

    //TODO SF coverage
    public DependentsSet getActualDependents(InputFileDetails jarChangeDetails, JarArchive jarArchive) {
        JarSnapshot existing = previousCompilation.getJarSnapshot(jarChangeDetails.getFile());
        if (jarChangeDetails.isAdded()) {
            return new DefaultDependentsSet();
        }

        if (existing == null) {
            //we don't know what classes were dependents of the jar in the previous build
            //for example, a class (in jar) with a constant might have changed into a class without a constant - we need to rebuild everything
            return new DefaultDependentsSet(true);
        }

        if (jarChangeDetails.isRemoved()) {
            DependentsSet allClasses = existing.getAllClasses();
            if (allClasses.isDependencyToAll()) {
                //at least one of the classes in jar is a 'dependency-to-all'.
                return allClasses;
            }
            //recompile all dependents of all the classes from jar
            return previousCompilation.getDependents(allClasses.getDependentClasses());
        }

        if (jarChangeDetails.isModified()) {
            JarSnapshot newSnapshot = jarSnapshotter.createSnapshot(jarArchive);
            DependentsSet affectedClasses = newSnapshot.getAffectedClassesSince(existing);
            if (affectedClasses.isDependencyToAll()) {
                //at least one of the classes changed in the jar is a 'dependency-to-all'
                return affectedClasses;
            }
            //recompile all dependents of the classes changed in the jar
            return previousCompilation.getDependents(affectedClasses.getDependentClasses());
        }

        throw new IllegalArgumentException("Unknown input file details provided: " + jarChangeDetails);
    }
}