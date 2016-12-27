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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassNamesCache;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class PreviousCompilation {

    private ClassSetAnalysis analysis;
    private LocalJarClasspathSnapshotStore classpathSnapshotStore;
    private final JarSnapshotCache jarSnapshotCache;
    private Map<File, JarSnapshot> jarSnapshots;
    private final ClassNamesCache classNamesCache;

    public PreviousCompilation(ClassSetAnalysis analysis, LocalJarClasspathSnapshotStore classpathSnapshotStore, JarSnapshotCache jarSnapshotCache, ClassNamesCache classNamesCache) {
        this.analysis = analysis;
        this.classpathSnapshotStore = classpathSnapshotStore;
        this.jarSnapshotCache = jarSnapshotCache;
        this.classNamesCache = classNamesCache;
    }

    public DependentsSet getDependents(Set<String> allClasses, Set<Integer> constants) {
        return analysis.getRelevantDependents(allClasses, constants);
    }

    public String getClassName(String path, boolean remove) {
        try {
            return classNamesCache.get(path);
        } finally {
            if (remove) {
                classNamesCache.remove(path);
            }
        }
    }

    public JarSnapshot getJarSnapshot(File file) {
        if (jarSnapshots == null) {
            JarClasspathSnapshotData data = classpathSnapshotStore.get();
            jarSnapshots = jarSnapshotCache.getJarSnapshots(data.getJarHashes());
        }
        JarSnapshot snapshot = jarSnapshots.get(file);
        if (snapshot == null) {
            snapshot = tryReconciliate(file);
        }
        return snapshot;
    }

    /**
     * This method is called whenever a new jar is added on classpath. Then we try to do our best effort
     * to find the previous version by stripping out "irrelevant" information from the file name in order
     * to discover a potential previous version. For example, if foo-1.3.0.jar was found on classpath on
     * the previous compilation and now we have foo-1.3.1.jar, then we would return the snapshot of
     * foo-1.3.0.jar, so that if 1.3.1 is binary compatible, we still avoid compilation.
     * @param file the jar file which wasn't found on a previous compilation
     * @return the snapshot of the previous version of this jar
     */
    private JarSnapshot tryReconciliate(File file) {
        String lookup = simpleName(file);
        ArrayList<File> candidates = Lists.newArrayListWithExpectedSize(1);
        for (File key : jarSnapshots.keySet()) {
            if (simpleName(key).equals(lookup)) {
                candidates.add(key);
            }
        }
        if (candidates.size()==1) {
            return jarSnapshots.get(candidates.get(0));
        }
        return null;
    }

    private static String simpleName(File file) {
        String name = file.getName().toLowerCase();
        name = name.replaceAll("(-([0-9]+\\.)+[0-9]+)|(-(snapshot|beta|rc|ga)-?[0-9]*)", "");
        return name;
    }

    public DependentsSet getDependents(String className, Set<Integer> newConstants) {
        Set<Integer> constants = Sets.difference(analysis.getData().getConstants(className), newConstants);
        return analysis.getRelevantDependents(className, constants);
    }
}
