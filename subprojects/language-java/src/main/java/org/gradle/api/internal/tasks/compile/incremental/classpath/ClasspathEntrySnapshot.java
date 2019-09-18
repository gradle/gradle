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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassChanges;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.internal.hash.HashCode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClasspathEntrySnapshot {

    private final ClasspathEntrySnapshotData data;
    private final ClassSetAnalysis analysis;

    public ClasspathEntrySnapshot(ClasspathEntrySnapshotData data) {
        this.data = data;
        this.analysis = new ClassSetAnalysis(data.getClassAnalysis());
    }

    public DependentsSet getAllClasses() {
        final Set<String> result = new HashSet<String>();
        for (Map.Entry<String, HashCode> cls : getHashes().entrySet()) {
            String className = cls.getKey();
            DependentsSet dependents = getClassAnalysis().getRelevantDependents(className, IntSets.EMPTY_SET);
            if (dependents.isDependencyToAll()) {
                return dependents;
            }
            result.add(className);
        }
        return DependentsSet.dependentClasses(Collections.emptySet(), result);
    }

    public IntSet getAllConstants(DependentsSet dependents) {
        IntSet result = new IntOpenHashSet();
        for (String cn : dependents.getAllDependentClasses()) {
            result.addAll(data.getClassAnalysis().getConstants(cn));
        }
        return result;
    }

    public IntSet getRelevantConstants(ClasspathEntrySnapshot other, Set<String> affectedClasses) {
        IntSet result = new IntOpenHashSet();
        for (String affectedClass : affectedClasses) {
            IntSet difference = new IntOpenHashSet(other.getData().getClassAnalysis().getConstants(affectedClass));
            difference.removeAll(data.getClassAnalysis().getConstants(affectedClass));
            result.addAll(difference);
        }
        return result;
    }

    public ClassChanges getChangedClassesSince(ClasspathEntrySnapshot other) {
        Set<String> modifiedClasses = modifiedSince(other);
        Set<String> addedClasses = addedSince(other);
        return new ClassChanges(modifiedClasses, addedClasses);
    }

    private Set<String> modifiedSince(ClasspathEntrySnapshot other) {
        final Set<String> modified = new HashSet<String>();
        for (Map.Entry<String, HashCode> otherClass : other.getHashes().entrySet()) {
            String otherClassName = otherClass.getKey();
            HashCode otherClassBytes = otherClass.getValue();
            HashCode thisClsBytes = getHashes().get(otherClassName);
            if (thisClsBytes == null || !thisClsBytes.equals(otherClassBytes)) {
                modified.add(otherClassName);
            }
        }
        return modified;
    }

    private Set<String> addedSince(ClasspathEntrySnapshot other) {
        Set<String> addedClasses = new HashSet<String>(getClasses());
        addedClasses.removeAll(other.getClasses());
        return addedClasses;
    }

    public HashCode getHash() {
        return data.getHash();
    }

    public Map<String, HashCode> getHashes() {
        return data.getHashes();
    }

    public ClassSetAnalysis getClassAnalysis() {
        return analysis;
    }

    public Set<String> getClasses() {
        return data.getHashes().keySet();
    }

    public ClasspathEntrySnapshotData getData() {
        return data;
    }
}
