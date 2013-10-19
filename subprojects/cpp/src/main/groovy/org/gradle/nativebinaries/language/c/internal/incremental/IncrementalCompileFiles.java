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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.api.internal.changedetection.state.DefaultHasher;
import org.gradle.api.internal.changedetection.state.Hasher;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.util.*;

public class IncrementalCompileFiles implements IncrementalCompilation {

    private final PersistentIndexedCache<File, FileState> fileStateCache;
    private final SourceDependencyParser dependencyParser;

    private final List<File> previous;
    private final List<File> recompile = new ArrayList<File>();
    private final List<File> removed = new ArrayList<File>();

    private final Map<File, Boolean> processed = new HashMap<File, Boolean>();

    // TODO:DAZ Use a caching hasher
    private final Hasher hasher = new DefaultHasher();

    public IncrementalCompileFiles(List<File> previousSourceFiles, PersistentIndexedCache<File, FileState> fileStateCache, SourceDependencyParser dependencyParser) {
        this.fileStateCache = fileStateCache;
        this.dependencyParser = dependencyParser;
        this.previous = previousSourceFiles;
        this.removed.addAll(this.previous);
    }

    public void processSource(File sourceFile) {
        removed.remove(sourceFile);
        if (checkChangedAndUpdateState(sourceFile) || !previous.contains(sourceFile)) {
            recompile.add(sourceFile);
        }
    }

    public boolean checkChangedAndUpdateState(File file) {
        boolean changed = false;

        if (processed.containsKey(file)) {
            return processed.get(file);
        }

        // Assume unchanged if we recurse to the same file due to dependency cycle
        processed.put(file, false);

        FileState state = findState(file);
        if (state == null) {
            state = new FileState();
        }

        byte[] currentHash = hasher.hash(file);
        if (hasChanged(state, currentHash)) {
            changed = true;
            state.setHash(currentHash);
            state.getDeps().addAll(dependencyParser.parseDependencies(file));
            saveState(file, state);
        }

        for (File dep : state.getDeps()) {
            Boolean depChanged = checkChangedAndUpdateState(dep);
            changed = changed || depChanged;
        }

        processed.put(file, changed);

        return changed;
    }

    private boolean hasChanged(FileState state, byte[] currentHash) {
        return !Arrays.equals(currentHash, state.getHash());
    }

    private FileState findState(File file) {
        return fileStateCache.get(file);
    }

    private void saveState(File file, FileState state) {
        fileStateCache.put(file, state);
    }

    public List<File> getRecompile() {
        return recompile;
    }

    public List<File> getRemoved() {
        return removed;
    }

    public Set<File> getTouched() {
        return processed.keySet();
    }
}
