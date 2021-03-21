/*
 * Copyright 2014 the original author or authors.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.internal.hash.HashCode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClasspathSnapshot {

    private final List<ClasspathEntrySnapshotData> entries;
    private Map<String, HashCode> classHashes;
    private ClassSetAnalysis aggregateAnalysis;

    public ClasspathSnapshot(List<ClasspathEntrySnapshotData> entries) {
        this.entries = entries;
    }

    public ClasspathChanges getChangesSince(ClasspathSnapshot other) {
        DependentsSet directChanges = getChangedClassesSince(other);
        DependentsSet transitiveChanges = other.getAnalysis().getRelevantDependents(directChanges.getAllDependentClasses(), IntSets.emptySet());
        DependentsSet allChanges = DependentsSet.merge(Arrays.asList(directChanges, transitiveChanges));
        IntSet changedConstants = getChangedConstants(other, allChanges);
        return new ClasspathChanges(allChanges, changedConstants);
    }

    private IntSet getChangedConstants(ClasspathSnapshot other, DependentsSet affectedClasses) {
        if (affectedClasses.isDependencyToAll()) {
            return IntSets.emptySet();
        }
        IntSet result = new IntOpenHashSet();
        for (String affectedClass : affectedClasses.getAllDependentClasses()) {
            IntSet previousConstants = other.getAnalysis().getConstants(affectedClass);
            IntSet currentConstants = getConstants(affectedClass);
            IntSet difference = new IntOpenHashSet(previousConstants);
            difference.removeAll(currentConstants);
            result.addAll(difference);
        }
        return result;
    }

    private DependentsSet getChangedClassesSince(ClasspathSnapshot other) {
        Map<String, HashCode> myHashes = getClassHashes();
        Map<String, HashCode> otherHashes = other.getClassHashes();
        ImmutableSet.Builder<String> changed = ImmutableSet.builder();
        for (String added : Sets.difference(myHashes.keySet(), otherHashes.keySet())) {
            changed.add(added);
        }
        for (Map.Entry<String, HashCode> removedOrChanged : Sets.difference(otherHashes.entrySet(), myHashes.entrySet())) {
            changed.add(removedOrChanged.getKey());
        }
        return DependentsSet.dependentClasses(ImmutableSet.of(), changed.build());
    }

    private ClassSetAnalysis getAnalysis() {
        if (aggregateAnalysis == null) {
            List<ClassSetAnalysisData> datas = Lists.transform(entries, ClasspathEntrySnapshotData::getClassAnalysis);
            aggregateAnalysis = new ClassSetAnalysis(ClassSetAnalysisData.merge(datas));
        }
        return aggregateAnalysis;
    }

    private Map<String, HashCode> getClassHashes() {
        if (classHashes == null) {
            int classCount = 0;
            for (ClasspathEntrySnapshotData entry : entries) {
                classCount += entry.getHashes().size();
            }
            classHashes = new HashMap<>(classCount);
            for (ClasspathEntrySnapshotData entry : Lists.reverse(entries)) {
                classHashes.putAll(entry.getHashes());
            }
        }
        return classHashes;
    }

    /**
     * A more efficient version of {@code getAnalysis().getConstants()}, as it avoids creating the merged
     * dependents analysis where it isn't needed.
     */
    private IntSet getConstants(String affectedClass) {
        return entries.stream()
            .map(ClasspathEntrySnapshotData::getClassAnalysis)
            .map(it -> it.getConstants(affectedClass))
            .filter(it -> !it.isEmpty()).findFirst()
            .orElse(IntSets.emptySet());
    }

    public static final class ClasspathChanges {
        private final DependentsSet dependents;
        private final IntSet constants;

        public ClasspathChanges(DependentsSet dependents, IntSet constants) {
            this.dependents = dependents;
            this.constants = constants;
        }

        public DependentsSet getDependents() {
            return dependents;
        }

        public IntSet getConstants() {
            return constants;
        }
    }
}
