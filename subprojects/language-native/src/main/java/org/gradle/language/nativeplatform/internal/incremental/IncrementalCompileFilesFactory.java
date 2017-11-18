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
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
        private final Map<File, IncludeDirectives> includeDirectivesMap = new HashMap<File, IncludeDirectives>();
        private final Map<File, FileVisitResult> filesWithNoMacroIncludes = new HashMap<File, FileVisitResult>();
        private boolean hasUnresolvedHeaders;

        DefaultIncementalCompileSourceProcessor(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        @Override
        public IncrementalCompilation getResult() {
            return new DefaultIncrementalCompilation(current.snapshot(), toRecompile, getRemovedSources(), discoveredInputs, existingHeaders, hasUnresolvedHeaders, includeDirectivesMap);
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
            FileVisitResult result = visitFile(sourceFile, new ArrayList<IncludeDirectives>(), new HashSet<File>());
            SourceFileState newState = new SourceFileState(fileSnapshot.getContent().getContentMd5(), ImmutableList.copyOf(result.includeFileStates));
            current.setState(sourceFile, newState);
            // Recompile this source file if:
            // - we don't know how/whether it has been compiled before
            // - the source or referenced include files contains include/import directives that cannot be resolved to an include file
            // - the source file or sequence of included files have changed in some way (the order/set/cardinality of the files has changed or the content of any file has changed)
            return previousState == null || result.result == IncludeFileResolutionResult.UnresolvedMacroIncludes || newState.hasChanged(previousState);
        }

        private FileVisitResult visitFile(File file, List<IncludeDirectives> visibleIncludeDirectives, Set<File> visited) {
            FileVisitResult previousResult = filesWithNoMacroIncludes.get(file);
            if (previousResult != null) {
                // A file that we can safely reuse the result for
                visibleIncludeDirectives.addAll(previousResult.includeFileDirectives);
                return previousResult;
            }

            if (!visited.add(file)) {
                // A cycle, treat as resolved here
                return new FileVisitResult();
            }

            FileSnapshot fileSnapshot = snapshotter.snapshotSelf(file);
            HashCode newHash = fileSnapshot.getContent().getContentMd5();
            IncludeDirectives includeDirectives = sourceIncludesParser.parseIncludes(file);
            includeDirectivesMap.put(file, includeDirectives);
            List<IncludeFileState> includedFileStates = new ArrayList<IncludeFileState>();
            List<IncludeDirectives> includedFileDirectives = new ArrayList<IncludeDirectives>();
            includedFileStates.add(new IncludeFileState(newHash, file));
            includedFileDirectives.add(includeDirectives);
            visibleIncludeDirectives.add(includeDirectives);

            IncludeFileResolutionResult result = IncludeFileResolutionResult.NoMacroIncludes;
            for (Include include : includeDirectives.getAll()) {
                if (include.getType() == IncludeType.MACRO && result == IncludeFileResolutionResult.NoMacroIncludes) {
                    result = IncludeFileResolutionResult.HasMacroIncludes;
                }
                SourceIncludesResolver.IncludeResolutionResult resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleIncludeDirectives);
                discoveredInputs.addAll(resolutionResult.getCheckedLocations());
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for include '{}' in source file '{}'. Assuming changed.", resolutionResult.getInclude(), file.getName());
                    result = IncludeFileResolutionResult.UnresolvedMacroIncludes;
                    hasUnresolvedHeaders = true;
                }
                for (File includeFile : resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile);
                    FileVisitResult includeVisitResult = visitFile(includeFile, visibleIncludeDirectives, visited);
                    if (includeVisitResult.result.ordinal() > result.ordinal()) {
                        result = includeVisitResult.result;
                    }
                    includedFileStates.addAll(includeVisitResult.includeFileStates);
                    includedFileDirectives.addAll(includeVisitResult.includeFileDirectives);
                }
            }

            FileVisitResult visitResult = new FileVisitResult(result, includedFileStates, includedFileDirectives);
            if (result == IncludeFileResolutionResult.NoMacroIncludes) {
                // No macro includes were seen in the include graph of this file, so the result can be reused if this file is seen again
                filesWithNoMacroIncludes.put(file, visitResult);
            }
            return visitResult;
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

    private enum IncludeFileResolutionResult {
        NoMacroIncludes,
        HasMacroIncludes, // but all resolved ok
        UnresolvedMacroIncludes
    }

    private static class FileVisitResult {
        final IncludeFileResolutionResult result;
        final List<IncludeFileState> includeFileStates;
        final List<IncludeDirectives> includeFileDirectives;

        FileVisitResult(IncludeFileResolutionResult result, List<IncludeFileState> includeFileStates, List<IncludeDirectives> includeFileDirectives) {
            this.result = result;
            this.includeFileStates = includeFileStates;
            this.includeFileDirectives = includeFileDirectives;
        }

        FileVisitResult() {
            result = IncludeFileResolutionResult.NoMacroIncludes;
            includeFileStates = Collections.emptyList();
            includeFileDirectives = Collections.emptyList();
        }
    }
}
