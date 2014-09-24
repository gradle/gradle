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
package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.cache.PersistentStateCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class IncrementalCompileProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileProcessor.class);

    private final PersistentStateCache<CompilationState> previousCompileStateCache;
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileSnapshotter snapshotter;

    public IncrementalCompileProcessor(PersistentStateCache<CompilationState> previousCompileStateCache, SourceIncludesResolver sourceIncludesResolver, SourceIncludesParser sourceIncludesParser,
                                       FileSnapshotter snapshotter) {
        this.previousCompileStateCache = previousCompileStateCache;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.sourceIncludesParser = sourceIncludesParser;
        this.snapshotter = snapshotter;
    }

    public IncrementalCompilation processSourceFiles(Collection<File> sourceFiles) {
        CompilationState previousCompileState = previousCompileStateCache.get();
        final IncrementalCompileFiles result = new IncrementalCompileFiles(previousCompileState);

        for (File sourceFile : sourceFiles) {
            result.processSource(sourceFile);
        }

        previousCompileStateCache.set(result.current);

        return new DefaultIncrementalCompilation(result.getModifiedSources(), result.getRemovedSources());
    }

    private class IncrementalCompileFiles {

        private final List<File> recompile = new ArrayList<File>();

        private final CompilationState previous;
        private final CompilationState current = new CompilationState();
        private final Map<File, Boolean> processed = new HashMap<File, Boolean>();

        public IncrementalCompileFiles(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        public void processSource(File sourceFile) {
            current.addSourceInput(sourceFile);
            if (checkChangedAndUpdateState(sourceFile) || !previous.getSourceInputs().contains(sourceFile)) {
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

            CompilationFileState previousState = previous.getState(file);
            CompilationFileState newState = new CompilationFileState(snapshotter.snapshot(file).getHash());

            if (!sameHash(previousState, newState)) {
                changed = true;
                newState.setSourceIncludes(sourceIncludesParser.parseIncludes(file));
            } else {
                newState.setSourceIncludes(previousState.getSourceIncludes());
            }

            newState.setResolvedIncludes(resolveIncludes(file, newState.getSourceIncludes()));
            // Compare the previous resolved includes with resolving now.
            if (!sameResolved(previousState, newState)) {
                changed = true;
            }

            current.setState(file, newState);

            for (ResolvedInclude dep : newState.getResolvedIncludes()) {
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

        private boolean sameHash(CompilationFileState previousState, CompilationFileState newState) {
            return previousState != null && Arrays.equals(newState.getHash(), previousState.getHash());
        }

        private boolean sameResolved(CompilationFileState previousState, CompilationFileState newState) {
            return previousState != null && newState.getResolvedIncludes().equals(previousState.getResolvedIncludes());
        }

        private Set<ResolvedInclude> resolveIncludes(File file, SourceIncludes sourceIncludes) {
            return sourceIncludesResolver.resolveIncludes(file, sourceIncludes);
        }

        public List<File> getModifiedSources() {
            return recompile;
        }

        public List<File> getRemovedSources() {
            List<File> removed = new ArrayList<File>();
            for (File previousSource : previous.getSourceInputs()) {
                if (!current.getSourceInputs().contains(previousSource)) {
                    removed.add(previousSource);
                }
            }
            return removed;
        }
    }
}
