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

import org.gradle.api.internal.tasks.compile.incremental.deps.AffectedClasses;
import org.gradle.api.internal.tasks.compile.incremental.deps.DefaultDependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependencyToAll;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.tasks.incremental.InputFileDetails;

public class JarChangeDependentsFinder {

    private final JarClasspathSnapshot jarClasspathSnapshot;
    private final PreviousCompilation previousCompilation;

    public JarChangeDependentsFinder(JarClasspathSnapshot jarClasspathSnapshot, PreviousCompilation previousCompilation) {
        this.jarClasspathSnapshot = jarClasspathSnapshot;
        this.previousCompilation = previousCompilation;
    }

    public DependentsSet getActualDependents(InputFileDetails jarChangeDetails, JarArchive jarArchive) {
        if (jarChangeDetails.isAdded()) {
            if (jarClasspathSnapshot.isAnyClassDuplicated(jarArchive)) {
                //at least one of the classes from the new jar is already present in jar classpath
                //to avoid calculation which class gets on the classpath first, rebuild all
                return new DependencyToAll("at least one of the classes of '" + jarArchive.file.getName() + "' is already present in classpath");
            } else {
                //none of the new classes in the jar are duplicated on classpath, don't rebuild
                return new DefaultDependentsSet();
            }
        }
        JarSnapshot previous = previousCompilation.getJarSnapshot(jarChangeDetails.getFile());

        if (previous == null) {
            //we don't know what classes were dependents of the jar in the previous build
            //for example, a class (in jar) with a constant might have changed into a class without a constant - we need to rebuild everything
            return new DependencyToAll("missing jar snapshot of '" + jarArchive.file.getName()  + "' from previous build");
        }

        if (jarChangeDetails.isRemoved()) {
            DependentsSet allClasses = previous.getAllClasses();
            if (allClasses.isDependencyToAll()) {
                return new DependencyToAll("at least one of the classes of removed jar '" + jarArchive.file.getName() + "' requires it");
            }
            //recompile all dependents of all the classes from jar
            return previousCompilation.getDependents(allClasses.getDependentClasses());
        }

        if (jarChangeDetails.isModified()) {
            JarSnapshot currentSnapshot = jarClasspathSnapshot.getSnapshot(jarArchive);
            AffectedClasses affected = currentSnapshot.getAffectedClassesSince(previous);
            if (affected.getAltered().isDependencyToAll()) {
                //at least one of the classes changed in the jar is a 'dependency-to-all'
                return affected.getAltered();
            }

            if (jarClasspathSnapshot.isAnyClassDuplicated(affected.getAdded())) {
                //A new duplicate class on classpath. As we don't fancy-handle classpath order right now, we don't know which class is on classpath first.
                //For safe measure rebuild everything
                return new DependencyToAll("at least one of the classes of modified jar '" + jarArchive.file.getName() + "' is already present in the classpath");
            }

            //recompile all dependents of the classes changed in the jar
            return previousCompilation.getDependents(affected.getAltered().getDependentClasses());
        }

        throw new IllegalArgumentException("Unknown input file details provided: " + jarChangeDetails);
    }
}