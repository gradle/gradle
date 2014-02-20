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

import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.cache.PersistentIndexedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class IncrementalCompileProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileProcessor.class);

    private static final String PREVIOUS_FILES = "previous";
    private final PersistentIndexedCache<File, FileState> fileStateCache;
    private final PersistentIndexedCache<String, List<File>> previousSourcesCache;
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileSnapshotter snapshotter;

    public IncrementalCompileProcessor(PersistentIndexedCache<File, FileState> fileStateCache, PersistentIndexedCache<String, List<File>> previousSourcesCache, SourceIncludesResolver sourceIncludesResolver, SourceIncludesParser sourceIncludesParser,
                                       FileSnapshotter snapshotter) {
        this.fileStateCache = fileStateCache;
        this.previousSourcesCache = previousSourcesCache;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.sourceIncludesParser = sourceIncludesParser;
        this.snapshotter = snapshotter;
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

    private void purgeRemoved(File removedSource, IncrementalCompileFiles result) {
        FileState state = fileStateCache.get(removedSource);
        if (state == null || result.getTouchedSources().contains(removedSource)) {
            return;
        }

        fileStateCache.remove(removedSource);

        // TODO:DAZ This isn't right: need to keep previous classpath, or previous resolution
        for (ResolvedInclude resolvedInclude : resolveIncludes(removedSource, state.getIncludes())) {
            if (!resolvedInclude.isUnknown()) {
                purgeRemoved(resolvedInclude.getFile(), result);
            }
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

            byte[] currentHash = snapshotter.snapshot(file).getHash();
            // TODO:DAZ Cache resolved include files separately from parsed includes, and reuse the parse cache between different variants/components
            Set<ResolvedInclude> resolvedIncludes;
            if (hasChanged(state, currentHash)) {
                SourceIncludes sourceIncludes = sourceIncludesParser.parseIncludes(file);
                resolvedIncludes = resolveIncludes(file, sourceIncludes);

                changed = true;
                state.setHash(currentHash);
                state.setIncludes(sourceIncludes);
                state.setResolvedIncludes(resolvedIncludes);
                saveState(file, state);
            } else {
                resolvedIncludes = resolveIncludes(file, state.getIncludes());

                // Compare the previous resolved includes with resolving now.
                if (!state.getResolvedIncludes().equals(resolvedIncludes)) {
                    changed = true;
                    state.setResolvedIncludes(resolvedIncludes);
                    saveState(file, state);
                }
            }

            for (ResolvedInclude dep : resolvedIncludes) {
                if (dep.isUnknown()) {
                    LOGGER.info(String.format("Cannot determine changed state of included '%s' in source file '%s'. Assuming changed.", dep.getInclude(), file.getName()));
                    changed = true;
                } else {
                    boolean depChanged = checkChangedAndUpdateState(dep.getFile());
                    changed = changed || depChanged;
                }
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

    private Set<ResolvedInclude> resolveIncludes(File file, SourceIncludes sourceIncludes) {
        return sourceIncludesResolver.resolveIncludes(file, sourceIncludes);
    }
}
