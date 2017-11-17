/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IncrementalCompileFilesFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileFilesFactory.class);
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileSystemSnapshotter snapshotter;

    public IncrementalCompileFilesFactory(SourceIncludesParser sourceIncludesParser, SourceIncludesResolver sourceIncludesResolver, FileSystemSnapshotter snapshotter) {
        this.sourceIncludesParser = sourceIncludesParser;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.snapshotter = snapshotter;
    }

    public IncementalCompileSourceProcessor filesFor(CompilationState previousCompileState) {
        return new DefaultIncementalCompileSourceProcessor(previousCompileState);
    }

    private class DefaultIncementalCompileSourceProcessor implements IncementalCompileSourceProcessor {
        private final CompilationState previous;
        private final BuildableCompilationState current = new BuildableCompilationState();
        private final List<File> toRecompile = new ArrayList<File>();
        private final Set<File> discoveredInputs = Sets.newHashSet();
        private final Set<File> existingHeaders = Sets.newHashSet();
        private final Map<File, IncludeDirectives> includeDirectivesMap = new LinkedHashMap<File, IncludeDirectives>();
        private boolean sourceFilesUseMacroIncludes;

        DefaultIncementalCompileSourceProcessor(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        @Override
        public IncrementalCompilation getResult() {
            return new DefaultIncrementalCompilation(current.snapshot(), toRecompile, getRemovedSources(), discoveredInputs, existingHeaders, sourceFilesUseMacroIncludes, includeDirectivesMap);
        }

        @Override
        public void processSource(File sourceFile) {
            current.addSourceInput(sourceFile);
            if (visitSourceFile(sourceFile)) {
                toRecompile.add(sourceFile);
            }
        }

        /**
         * @return true if this source file requires recompilation, false otherwise.
         */
        private boolean visitSourceFile(File sourceFile) {
            FileSnapshot fileSnapshot = snapshotter.snapshotSelf(sourceFile);
            if (fileSnapshot.getType() != FileType.RegularFile) {
                // Skip things that aren't files
                return false;
            }

            SourceFileState previousState = previous.getState(sourceFile);
            List<IncludeDirectives> includeDirectives = new ArrayList<IncludeDirectives>();
            List<IncludeFileState> includedFiles = new ArrayList<IncludeFileState>();
            Set<File> visited = new HashSet<File>();
            FileVisitResult result = visitFile(sourceFile, includedFiles, includeDirectives, visited);
            SourceFileState newState = new SourceFileState(fileSnapshot.getContent().getContentMd5(), ImmutableList.copyOf(includedFiles));
            current.setState(sourceFile, newState);
            return previousState == null || result == FileVisitResult.Unresolved || newState.hasChanged(previousState);
        }

        private FileVisitResult visitFile(File file, List<IncludeFileState> includedFiles, List<IncludeDirectives> visibleIncludeDirectives, Set<File> visited) {
            if (!visited.add(file)) {
                // A cycle, treat as resolved here
                return FileVisitResult.Resolved;
            }
            FileSnapshot fileSnapshot = snapshotter.snapshotSelf(file);
            HashCode newHash = fileSnapshot.getContent().getContentMd5();
            // TODO - cache this here
            IncludeDirectives includeDirectives = sourceIncludesParser.parseIncludes(file);
            includeDirectivesMap.put(file, includeDirectives);
            includedFiles.add(new IncludeFileState(newHash, file));
            visibleIncludeDirectives.add(includeDirectives);

            FileVisitResult result = FileVisitResult.Resolved;
            for (Include include : includeDirectives.getAll()) {
                // TODO - remember the result for an include file graph that did not reference any macros
                SourceIncludesResolver.IncludeResolutionResult resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleIncludeDirectives);
                discoveredInputs.addAll(resolutionResult.getCheckedLocations());
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for include '{}' in source file '{}'. Assuming changed.", resolutionResult.getInclude(), file.getName());
                    result = FileVisitResult.Unresolved;
                    sourceFilesUseMacroIncludes = true;
                }
                for (File includeFile : resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile);
                    FileVisitResult headerResult = visitFile(includeFile, includedFiles, visibleIncludeDirectives, visited);
                    if (headerResult != FileVisitResult.Resolved) {
                        result = headerResult;
                    }
                }
            }

            return result;
        }

        private List<File> getRemovedSources() {
            List<File> removed = new ArrayList<File>();
            for (File previousSource : previous.getSourceInputs()) {
                if (!current.getSourceInputs().contains(previousSource)) {
                    removed.add(previousSource);
                }
            }
            return removed;
        }
    }

    private enum FileVisitResult {
        Resolved,
        Unresolved
    }
}
