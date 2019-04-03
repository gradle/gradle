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

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;

public class ClasspathSnapshot {

    private final LinkedHashMap<File, ClasspathEntrySnapshot> entrySnapshots;
    private final ClasspathSnapshotData data;

    public ClasspathSnapshot(LinkedHashMap<File, ClasspathEntrySnapshot> entrySnapshots, ClasspathSnapshotData data) {
        this.entrySnapshots = entrySnapshots;
        this.data = data;
    }

    public ClasspathEntrySnapshot getSnapshot(File file) {
        return entrySnapshots.get(file);
    }

    public Set<File> getEntries() {
        return entrySnapshots.keySet();
    }

    public boolean isAnyClassDuplicated(Set<String> classNames) {
        boolean noCommonElements = Collections.disjoint(data.getDuplicateClasses(), classNames);
        return !noCommonElements;
    }

    public ClasspathSnapshotData getData() {
        return data;
    }

    public boolean isAnyClassDuplicated(File classpathEntry) {
        ClasspathEntrySnapshot snapshot = getSnapshot(classpathEntry);
        return isAnyClassDuplicated(snapshot.getClasses());
    }
}
