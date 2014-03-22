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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.internal.hash.Hasher;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.graph.ClassDependencyInfo;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class ClassSnapshotter {
    private Hasher hasher;
    private ClassDependenciesAnalyzer analyzer;

    public ClassSnapshotter(Hasher hasher, ClassDependenciesAnalyzer analyzer) {
        this.hasher = hasher;
        this.analyzer = analyzer;
    }

    public ClassSnapshot createSnapshot(String className, File classFile, ClassDependencyInfo parentDependencyInfo) {
        boolean dependentToAll;
        try {
            dependentToAll = analyzer.getClassAnalysis(className, classFile).isDependentToAll();
        } catch (IOException e) {
            throw new RuntimeException("Problems creating jar snapshot.", e);
        }
        byte[] hash = hasher.hash(classFile);
        if (dependentToAll) {
            return new ClassSnapshot(hash, null);
        } else {
            Set<String> dependents = parentDependencyInfo.getRelevantDependents(className);
            return new ClassSnapshot(hash, dependents);
        }
    }
}
