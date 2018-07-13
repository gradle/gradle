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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessorPathStore;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreviousCompilation {

    private ClassSetAnalysis analysis;
    private LocalClasspathSnapshotStore classpathSnapshotStore;
    private final ClasspathEntrySnapshotCache classpathEntrySnapshotCache;
    private final AnnotationProcessorPathStore annotationProcessorPathStore;
    private Map<File, ClasspathEntrySnapshot> snapshots;

    public PreviousCompilation(ClassSetAnalysis analysis, LocalClasspathSnapshotStore classpathSnapshotStore, ClasspathEntrySnapshotCache classpathEntrySnapshotCache, AnnotationProcessorPathStore annotationProcessorPathStore) {
        this.analysis = analysis;
        this.classpathSnapshotStore = classpathSnapshotStore;
        this.classpathEntrySnapshotCache = classpathEntrySnapshotCache;
        this.annotationProcessorPathStore = annotationProcessorPathStore;
    }

    public DependentsSet getDependents(Set<String> allClasses, IntSet constants) {
        return analysis.getRelevantDependents(allClasses, constants);
    }

    public String getClassName(String path) {
        return analysis.getData().getClassNameForFile(path);
    }

    public ClasspathEntrySnapshot getClasspathEntrySnapshot(File file) {
        if (snapshots == null) {
            ClasspathSnapshotData data = classpathSnapshotStore.get();
            snapshots = classpathEntrySnapshotCache.getClasspathEntrySnapshots(data.getFileHashes());
        }
        return snapshots.get(file);
    }

    public DependentsSet getDependents(String className, IntSet newConstants) {
        IntSet constants = new IntOpenHashSet(analysis.getData().getConstants(className));
        constants.removeAll(newConstants);
        return analysis.getRelevantDependents(className, constants);
    }

    public DependentsSet getAggregatedTypes() {
        return analysis.getAggregatedTypes();
    }

    public Map<File, ClasspathEntrySnapshot> getSnapshots() {
        if (snapshots == null) {
            ClasspathSnapshotData data = classpathSnapshotStore.get();
            snapshots = classpathEntrySnapshotCache.getClasspathEntrySnapshots(data.getFileHashes());
        }
        return Collections.unmodifiableMap(snapshots);
    }

    public List<File> getAnnotationProcessorPath() {
        return annotationProcessorPathStore.get();
    }
}
