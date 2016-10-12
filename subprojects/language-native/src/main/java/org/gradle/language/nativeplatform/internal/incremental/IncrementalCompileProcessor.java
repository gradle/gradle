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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.cache.PersistentStateCache;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IncrementalCompileProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileProcessor.class);

    private final PersistentStateCache<CompilationState> previousCompileStateCache;
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileHasher hasher;

    public IncrementalCompileProcessor(PersistentStateCache<CompilationState> previousCompileStateCache, SourceIncludesResolver sourceIncludesResolver, SourceIncludesParser sourceIncludesParser, FileHasher hasher) {
        this.previousCompileStateCache = previousCompileStateCache;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.sourceIncludesParser = sourceIncludesParser;
        this.hasher = hasher;
    }

    public IncrementalCompilation processSourceFiles(Collection<File> sourceFiles) {
        CompilationState previousCompileState = previousCompileStateCache.get();
        final IncrementalCompileFiles result = new IncrementalCompileFiles(previousCompileState);

        for (File sourceFile : sourceFiles) {
            result.processSource(sourceFile);
        }

        return new DefaultIncrementalCompilation(result.current.snapshot(), result.getModifiedSources(), result.getRemovedSources(), result.getDiscoveredInputs());
    }

    private class IncrementalCompileFiles {

        private final CompilationState previous;
        private final BuildableCompilationState current = new BuildableCompilationState();

        private final Map<File, Boolean> processed = new HashMap<File, Boolean>();
        private final List<File> toRecompile = new ArrayList<File>();
        private final Set<File> discoveredInputs = Sets.newHashSet();

        public IncrementalCompileFiles(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        public void processSource(File sourceFile) {
            current.addSourceInput(sourceFile);
            if (checkChangedAndUpdateState(sourceFile) || !previous.getSourceInputs().contains(sourceFile)) {
                toRecompile.add(sourceFile);
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
            HashCode newHash = hasher.hash(file);

            IncludeDirectives includeDirectives;
            if (!sameHash(previousState, newHash)) {
                changed = true;
                includeDirectives = sourceIncludesParser.parseIncludes(file);
            } else {
                includeDirectives = previousState.getIncludeDirectives();
            }
            SourceIncludesResolver.ResolvedSourceIncludes resolutionResult = resolveIncludes(file, includeDirectives);

            CompilationFileState newState = new CompilationFileState(newHash, includeDirectives, ImmutableSet.copyOf(resolutionResult.getResolvedIncludes()));

            discoveredInputs.addAll(resolutionResult.getCheckedLocations());

            // Compare the previous resolved includes with resolving now.
            if (!sameResolved(previousState, newState)) {
                changed = true;
            }

            current.setState(file, newState);

            for (ResolvedInclude dep : newState.getResolvedIncludes()) {
                if (dep.isUnknown()) {
                    LOGGER.info("Cannot determine changed state of included '{}' in source file '{}'. Assuming changed.", dep.getInclude(), file.getName());
                    changed = true;
                } else {
                    boolean depChanged = checkChangedAndUpdateState(dep.getFile());
                    changed = changed || depChanged;
                }
            }

            processed.put(file, changed);

            return changed;
        }

        private boolean sameHash(CompilationFileState previousState, HashCode newHash) {
            return previousState != null && newHash.equals(previousState.getHash());
        }

        private boolean sameResolved(CompilationFileState previousState, CompilationFileState newState) {
            return previousState != null && newState.getResolvedIncludes().equals(previousState.getResolvedIncludes());
        }

        private SourceIncludesResolver.ResolvedSourceIncludes resolveIncludes(File file, IncludeDirectives includeDirectives) {
            return sourceIncludesResolver.resolveIncludes(file, includeDirectives);
        }

        public List<File> getModifiedSources() {
            return toRecompile;
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

        public Set<File> getDiscoveredInputs() {
            return discoveredInputs;
        }
    }
}
