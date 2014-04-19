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

package org.gradle.api.internal.tasks.compile.incremental.model;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;
import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshot;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshotCache;

import java.io.File;
import java.util.Set;

public class PreviousCompilation {

    private ClassDependencyInfo dependencyInfo;
    private JarSnapshotCache jarSnapshotCache;

    public PreviousCompilation(ClassDependencyInfo dependencyInfo, JarSnapshotCache jarSnapshotCache) {
        this.dependencyInfo = dependencyInfo;
        this.jarSnapshotCache = jarSnapshotCache;
    }

    public DependentsSet getDependents(Set<String> allClasses) {
        return dependencyInfo.getRelevantDependents(allClasses);
    }

    public JarSnapshot getJarSnapshot(File file) {
        return jarSnapshotCache.getSnapshot(file);
    }
}
