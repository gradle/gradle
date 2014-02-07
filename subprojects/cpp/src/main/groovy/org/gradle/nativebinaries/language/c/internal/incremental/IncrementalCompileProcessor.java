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

import org.gradle.api.internal.hash.Hasher;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.util.*;

public class IncrementalCompileProcessor {

    private static final String PREVIOUS_FILES = "previous";
    private final PersistentIndexedCache<File, FileState> fileStateCache;
    private final PersistentIndexedCache<String, List<File>> previousSourcesCache;
    private final SourceDependencyParser dependencyParser;
    private final Hasher hasher;

    public IncrementalCompileProcessor(PersistentIndexedCache<File, FileState> fileStateCache, PersistentIndexedCache<String, List<File>> previousSourcesCache, SourceDependencyParser dependencyParser, Hasher hasher) {
        this.fileStateCache = fileStateCache;
        this.previousSourcesCache = previousSourcesCache;
        this.dependencyParser = dependencyParser;
        this.hasher = hasher;
    }

    public IncrementalCompilation processSourceFiles(Collection<File> sourceFiles) {
        List<File> previousSources = previousSourcesCache.get(PREVIOUS_FILES);
        previousSources = previousSources == null ? Collections.<File>emptyList() : previousSources;
        final IncrementalCompileFiles result = new IncrementalCompileFiles(previousSources);

        for (File sourceFile : sourceFiles) {
            result.processSource(sourceFile);
        }

        for (File removed : result.getRemovedSources()) {
            purgeRemoved(removed, result);
        }

        previousSourcesCache.put(PREVIOUS_FILES, new ArrayList<File>(sourceFiles));

        return new DefaultIncrementalCompilation(result.getModifiedSources(), result.getRemovedSources());
    }

    private void purgeRemoved(File removed, IncrementalCompileFiles result) {
        FileState state = fileStateCache.get(removed);
        if (state == null || result.getTouchedSources().contains(removed)) {
            return;
        }

        fileStateCache.remove(removed);

        for (File file : state.getDependencies()) {
            purgeRemoved(file, result);
        }
    }

    private class IncrementalCompileFiles {

        private final List<File> recompile = new ArrayList<File>();
        private final List<File> removed = new ArrayList<File>();
        private final List<File> previous;

        private final Map<File, Boolean> processed = new HashMap<File, Boolean>();

        public IncrementalCompileFiles(List<File> previousSourceFiles) {
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

            if (!file.exists()) {
                return true;
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
                state.setDependencies(dependencyParser.parseDependencies(file));
                saveState(file, state);
            }

            for (File dep : state.getDependencies()) {
                boolean depChanged = checkChangedAndUpdateState(dep);
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

        public List<File> getModifiedSources() {
            return recompile;
        }

        public List<File> getRemovedSources() {
            return removed;
        }

        public Set<File> getTouchedSources() {
            return processed.keySet();
        }
    }
}
