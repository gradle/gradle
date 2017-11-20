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

import com.google.common.collect.ImmutableSet;
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

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IncrementalCompileFilesFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalCompileFilesFactory.class);
    private static final String IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME = "org.gradle.internal.native.headers.unresolved.dependencies.ignore";

    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileSystemSnapshotter snapshotter;
    private final boolean ignoreUnresolvedHeadersInDependencies;

    public IncrementalCompileFilesFactory(SourceIncludesParser sourceIncludesParser, SourceIncludesResolver sourceIncludesResolver, FileSystemSnapshotter snapshotter) {
        this.sourceIncludesParser = sourceIncludesParser;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.snapshotter = snapshotter;
        this.ignoreUnresolvedHeadersInDependencies = Boolean.getBoolean(IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME);
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
        private final Map<File, FileDetails> visitedFiles = new HashMap<File, FileDetails>();
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
            FileVisitResult result = visitFile(sourceFile, new ArrayList<IncludeDirectives>(), new HashSet<File>(), true);
            SourceFileState newState = new SourceFileState(fileSnapshot.getContent().getContentMd5(), ImmutableSet.copyOf(result.includeFileStates));
            current.setState(sourceFile, newState);
            includeDirectivesMap.put(sourceFile, result.includeDirectives);
            // Recompile this source file if:
            // - we don't know how/whether it has been compiled before
            // - the source or referenced include files contains include/import directives that cannot be resolved to an include file
            // - the source file or sequence of included files have changed in some way (the order/set/cardinality of the files has changed or the content of any file has changed)
            return previousState == null || result.result == IncludeFileResolutionResult.UnresolvedMacroIncludes || newState.hasChanged(previousState);
        }

        private FileVisitResult visitFile(File file, List<IncludeDirectives> visibleIncludeDirectives, Set<File> visited, boolean isSourceFile) {
            FileDetails fileDetails = visitedFiles.get(file);
            if (fileDetails != null && fileDetails.results != null) {
                // A file that we can safely reuse the result for
                visibleIncludeDirectives.addAll(fileDetails.results.includeFileDirectives);
                return fileDetails.results;
            }

            if (!visited.add(file)) {
                // A cycle, treat as resolved here
                return new FileVisitResult();
            }

            if (fileDetails == null) {
                FileSnapshot fileSnapshot = snapshotter.snapshotSelf(file);
                HashCode newHash = fileSnapshot.getContent().getContentMd5();
                IncludeDirectives includeDirectives = sourceIncludesParser.parseIncludes(file);
                fileDetails = new FileDetails(new IncludeFileState(newHash, file), includeDirectives);
                visitedFiles.put(file, fileDetails);
            }

            Set<IncludeFileState> includedFileStates = new LinkedHashSet<IncludeFileState>();
            Set<IncludeDirectives> includedFileDirectives = new LinkedHashSet<IncludeDirectives>();
            visibleIncludeDirectives.add(fileDetails.directives);

            IncludeFileResolutionResult result = IncludeFileResolutionResult.NoMacroIncludes;
            for (Include include : fileDetails.directives.getAll()) {
                if (include.getType() == IncludeType.MACRO && result == IncludeFileResolutionResult.NoMacroIncludes) {
                    result = IncludeFileResolutionResult.HasMacroIncludes;
                }
                SourceIncludesResolver.IncludeResolutionResult resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleIncludeDirectives);
                discoveredInputs.addAll(resolutionResult.getCheckedLocations());
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for include '{}' in source file '{}'. Assuming changed.", resolutionResult.getInclude(), file.getName());
                    result = IncludeFileResolutionResult.UnresolvedMacroIncludes;
                    if (isSourceFile || !ignoreUnresolvedHeadersInDependencies) {
                        hasUnresolvedHeaders = true;
                    }
                }
                for (File includeFile : resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile);
                    FileVisitResult includeVisitResult = visitFile(includeFile, visibleIncludeDirectives, visited, false);
                    if (includeVisitResult.result.ordinal() > result.ordinal()) {
                        result = includeVisitResult.result;
                    }
                    includeVisitResult.collectDependencies(includedFileStates, includedFileDirectives);
                }
            }

            FileVisitResult visitResult = new FileVisitResult(result, fileDetails.state, fileDetails.directives, includedFileStates, includedFileDirectives);
            if (result == IncludeFileResolutionResult.NoMacroIncludes) {
                // No macro includes were seen in the include graph of this file, so the result can be reused if this file is seen again
                fileDetails.results = visitResult;
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

    /**
     * Details of a file that are independent of where the file appears in the file include graph.
     */
    private static class FileDetails {
        final IncludeFileState state;
        final IncludeDirectives directives;
        // Non-null when the result of visiting this file can be reused
        @Nullable
        FileVisitResult results;

        FileDetails(IncludeFileState state, IncludeDirectives directives) {
            this.state = state;
            this.directives = directives;
        }
    }

    /**
     * Details of a file included in a specific location in the file include graph.
     */
    private static class FileVisitResult {
        private final IncludeFileResolutionResult result;
        private final IncludeFileState fileState;
        private final IncludeDirectives includeDirectives;
        private final Set<IncludeFileState> includeFileStates;
        private final Set<IncludeDirectives> includeFileDirectives;

        FileVisitResult(IncludeFileResolutionResult result, IncludeFileState fileState, IncludeDirectives includeDirectives, Set<IncludeFileState> dependentFiles, Set<IncludeDirectives> dependentIncludeDirectives) {
            this.result = result;
            this.fileState = fileState;
            this.includeDirectives = includeDirectives;
            this.includeFileStates = dependentFiles;
            this.includeFileDirectives = dependentIncludeDirectives;
        }

        FileVisitResult() {
            result = IncludeFileResolutionResult.NoMacroIncludes;
            fileState = null;
            includeDirectives = null;
            includeFileStates = Collections.emptySet();
            includeFileDirectives = Collections.emptySet();
        }

        void collectDependencies(Collection<IncludeFileState> fileStates, Collection<IncludeDirectives> directives) {
            if (fileState != null) {
                fileStates.add(fileState);
                fileStates.addAll(includeFileStates);
                directives.add(includeDirectives);
                directives.addAll(includeFileDirectives);
            }
        }
    }
}
