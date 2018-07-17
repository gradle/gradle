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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshot;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathEntrySnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreviousCompilation {

    private final PreviousCompilationData data;
    private final ClassSetAnalysis classAnalysis;
    private final ClasspathEntrySnapshotCache classpathEntrySnapshotCache;
    private Map<File, ClasspathEntrySnapshot> snapshots;

    public PreviousCompilation(PreviousCompilationData data, ClasspathEntrySnapshotCache classpathEntrySnapshotCache) {
        this.data = data;
        this.classAnalysis = new ClassSetAnalysis(data.getClassAnalysis());
        this.classpathEntrySnapshotCache = classpathEntrySnapshotCache;
    }

    public DependentsSet getDependents(Set<String> allClasses, IntSet constants) {
        return classAnalysis.getRelevantDependents(allClasses, constants);
    }

    public String getClassName(String path) {
        return data.getClassAnalysis().getClassNameForFile(path);
    }

    public ClasspathEntrySnapshot getClasspathEntrySnapshot(File file) {
        initSnapshots();
        return snapshots.get(file);
    }

    public Map<File, ClasspathEntrySnapshot> getSnapshots() {
        initSnapshots();
        return Collections.unmodifiableMap(snapshots);
    }

    private void initSnapshots() {
        if (snapshots == null) {
            ClasspathSnapshotData classpathSnapshot = data.getClasspathSnapshot();
            snapshots = classpathEntrySnapshotCache.getClasspathEntrySnapshots(classpathSnapshot.getFileHashes());
        }
    }

    public DependentsSet getDependents(String className, IntSet newConstants) {
        IntSet constants = new IntOpenHashSet(classAnalysis.getData().getConstants(className));
        constants.removeAll(newConstants);
        return classAnalysis.getRelevantDependents(className, constants);
    }

    public DependentsSet getAggregatedTypes() {
        return classAnalysis.getAggregatedTypes();
    }


    public List<File> getAnnotationProcessorPath() {
        return data.getAnnotationProcessorPath();
    }
}
