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

import org.gradle.api.Action;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;

public class JarClasspathSnapshot {

    private final LinkedHashMap<File, JarSnapshot> jarSnapshots;
    private final JarClasspathSnapshotData data;

    public JarClasspathSnapshot(LinkedHashMap<File, JarSnapshot> jarSnapshots, JarClasspathSnapshotData data) {
        this.jarSnapshots = jarSnapshots;
        this.data = data;
    }

    public JarSnapshot getSnapshot(JarArchive jarArchive) {
        return jarSnapshots.get(jarArchive.file);
    }

    public JarSnapshot getSnapshot(File file) {
        return jarSnapshots.get(file);
    }

    public Set<File> getJars() {
        return jarSnapshots.keySet();
    }

    public boolean isAnyClassDuplicated(Set<String> classNames) {
        boolean noCommonElements = Collections.disjoint(data.getDuplicateClasses(), classNames);
        return !noCommonElements;
    }

    public JarClasspathSnapshotData getData() {
        return data;
    }

    public boolean isAnyClassDuplicated(JarArchive jarArchive) {
        JarSnapshot snapshot = getSnapshot(jarArchive);
        return isAnyClassDuplicated(snapshot.getClasses());
    }

    public void forEachSnapshot(Action<? super JarSnapshot> action) {
        for (JarSnapshot jarSnapshot : jarSnapshots.values()) {
            action.execute(jarSnapshot);
        }
    }
}
