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
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final IncrementalCompileSourceProcessorCache sourceProcessorCache;
    private final SourceIncludesSearchPath searchPath;
    private final boolean ignoreUnresolvedHeadersInDependencies;

    public IncrementalCompileFilesFactory(SourceIncludesParser sourceIncludesParser, SourceIncludesResolver sourceIncludesResolver, FileSystemSnapshotter fileSystemSnapshotter, IncrementalCompileSourceProcessorCache sourceProcessorCache, SourceIncludesSearchPath searchPath) {
        this.sourceIncludesParser = sourceIncludesParser;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.sourceProcessorCache = sourceProcessorCache;
        this.searchPath = searchPath;
        this.ignoreUnresolvedHeadersInDependencies = Boolean.getBoolean(IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME);
    }

    public IncementalCompileSourceProcessor filesFor(CompilationState previousCompileState) {
        return new DefaultIncementalCompileSourceProcessor(previousCompileState);
    }

    private class DefaultIncementalCompileSourceProcessor implements IncementalCompileSourceProcessor {
        private final CompilationState previous;
        private final BuildableCompilationState current = new BuildableCompilationState();
        private final List<File> toRecompile = new ArrayList<File>();
        private final Set<File> existingHeaders = Sets.newHashSet();
        private final Map<File, IncludeDirectives> includeDirectivesMap = new HashMap<File, IncludeDirectives>();
        private final Map<File, FileDetails> visitedFiles = new HashMap<File, FileDetails>();
        private boolean hasUnresolvedHeaders;
        private final Map<String, IncludeFile> resolutionCache = new HashMap<String, IncludeFile>();

        DefaultIncementalCompileSourceProcessor(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        @Override
        public IncrementalCompilation getResult() {
            return new DefaultIncrementalCompilation(current.snapshot(), toRecompile, getRemovedSources(), existingHeaders, hasUnresolvedHeaders, includeDirectivesMap);
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
            FileSnapshot fileSnapshot = fileSystemSnapshotter.snapshotSelf(sourceFile);
            if (fileSnapshot.getType() != FileType.RegularFile) {
                // Skip things that aren't files
                return false;
            }

            SourceFileState previousState = previous.getState(sourceFile);
            CollectingMacroLookup visibleMacros = new CollectingMacroLookup();
            FileVisitResult result = visitFile(sourceFile, fileSnapshot, visibleMacros, new HashSet<File>(), true);
            LinkedHashSet<IncludeFileState> includedFiles = new LinkedHashSet<IncludeFileState>();
            result.collectFilesInto(includedFiles);
            SourceFileState newState = new SourceFileState(fileSnapshot.getContent().getContentMd5(), ImmutableSet.copyOf(includedFiles));
            current.setState(sourceFile, newState);
            includeDirectivesMap.put(sourceFile, result.getIncludeDirectives());
            // Recompile this source file if:
            // - we don't know how/whether it has been compiled before
            // - the source or referenced include files contains include/import directives that cannot be resolved to an include file
            // - the source file or sequence of included files have changed in some way (the order/set/cardinality of the files has changed or the content of any file has changed)
            return previousState == null || result.getResult() == IncludeFileResolutionResult.UnresolvedMacroIncludes || newState.hasChanged(previousState);
        }

        private FileVisitResult visitFile(File file, FileSnapshot fileSnapshot, CollectingMacroLookup visibleMacros, Set<File> visited, boolean isSourceFile) {
            FileDetails fileDetails = visitedFiles.get(file);
            if (fileDetails == null) {
                Collection<FileVisitResult> fileDetailsCollection = sourceProcessorCache.get(file);
                for (FileVisitResult candidate : fileDetailsCollection) {
                    if (candidate.canReuse(new ResolutionContext(searchPath, resolutionCache))) {
                        fileDetails = new FileDetails(null, null);
                        fileDetails.results = candidate;
                        visitedFiles.put(file, fileDetails);
                        break;
                    }
                }
            }

            if (fileDetails != null && fileDetails.results != null) {
                // A file that we can safely reuse the result for
                visibleMacros.append(fileDetails.results);
                return fileDetails.results;
            }

            if (!visited.add(file)) {
                // A cycle, treat as resolved here
                return new FileVisitResultImpl(file);
            }

            if (fileDetails == null) {
                HashCode newHash = fileSnapshot.getContent().getContentMd5();
                IncludeDirectives includeDirectives = sourceIncludesParser.parseIncludes(file);
                fileDetails = new FileDetails(new IncludeFileState(newHash, file), includeDirectives);
                visitedFiles.put(file, fileDetails);
            }

            CollectingMacroLookup includedFileDirectives = new CollectingMacroLookup();
            visibleMacros.append(file, fileDetails.directives);

            List<FileVisitResult> included = new ArrayList<FileVisitResult>(fileDetails.directives.getAll().size());
            Set<IncludeFile> includeFiles = new HashSet<IncludeFile>();
            IncludeFileResolutionResult result = IncludeFileResolutionResult.NoMacroIncludes;
            for (Include include : fileDetails.directives.getAll()) {
                if (include.getType() == IncludeType.MACRO && result == IncludeFileResolutionResult.NoMacroIncludes) {
                    result = IncludeFileResolutionResult.HasMacroIncludes;
                }
                SourceIncludesResolver.IncludeResolutionResult resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleMacros);
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for '{}' in source file '{}'. Assuming changed.", include.getAsSourceText(), file.getName());
                    if (isSourceFile || !ignoreUnresolvedHeadersInDependencies) {
                        hasUnresolvedHeaders = true;
                        result = IncludeFileResolutionResult.UnresolvedMacroIncludes;
                    }
                }
                includeFiles.addAll(resolutionResult.getFiles());
                for (IncludeFile includeFile : resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile.getFile());
                    FileVisitResult includeVisitResult = visitFile(includeFile.getFile(), includeFile.getSnapshot(), visibleMacros, visited, false);
                    if (includeVisitResult.getResult().ordinal() > result.ordinal()) {
                        result = includeVisitResult.getResult();
                    }
                    includeVisitResult.collectDependencies(includedFileDirectives);
                    included.add(includeVisitResult);
                }
            }

            FileVisitResult visitResult = new FileVisitResultImpl(file, result, fileDetails.state, fileDetails.directives, included, includedFileDirectives, includeFiles);
            if (result == IncludeFileResolutionResult.NoMacroIncludes) {
                // No macro includes were seen in the include graph of this file, so the result can be reused if this file is seen again
                fileDetails.results = visitResult;
                sourceProcessorCache.put(file, visitResult);
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
     * Context used when verifying the file visit result for reuse.
     */
    private static class ResolutionContext {
        private final SourceIncludesSearchPath searchPath;
        private final Map<String, IncludeFile> cache;
        Set<File> visited = new HashSet<File>();

        ResolutionContext(SourceIncludesSearchPath searchPath, Map<String, IncludeFile> cache) {
            this.searchPath = searchPath;
            this.cache = cache;
        }

        /**
         * Returns {@code true} if the specified file haven't been check or {@code false} otherwise.
         */
        public boolean canVisit(File file) {
            return visited.add(file);
        }

        /**
         * Return {@code true} if the file match the previous resolution result or {@code false} otherwise.
         */
        public boolean canReuseResolutionResult(File sourceFile, IncludeFile includeFile) {
            IncludeFile foundFile;
            if (includeFile.getIncludedType() == IncludeFile.IncludedType.QUOTED) {
                foundFile = searchPath.searchForDependency(includeFile.getInclude(), sourceFile);
            } else {
                foundFile = searchPath.searchForDependency(includeFile.getInclude());
            }

            if (!includeFile.equals(foundFile)) {
                return false;
            }
            return true;
        }
    }

    /**
     * Details of a file included in a specific location in the file include graph.
     */
    public interface FileVisitResult extends CollectingMacroLookup.MacroSource {
        void collectDependencies(CollectingMacroLookup directives);
        void collectFilesInto(Set<IncludeFileState> files);
        IncludeFileResolutionResult getResult();
        IncludeDirectives getIncludeDirectives();

        /**
         * Verify if this file visit result can be reuse in the specified resolution context.
         */
        boolean canReuse(ResolutionContext resolutionContext);
    }

    private static class FileVisitResultImpl implements FileVisitResult {
        private final File file;
        private final IncludeFileResolutionResult result;
        private final IncludeFileState fileState;
        private final IncludeDirectives includeDirectives;
        private final List<FileVisitResult> included;
        private final CollectingMacroLookup includeFileDirectives;
        private final Set<IncludeFile> includeFiles;

        FileVisitResultImpl(File file, IncludeFileResolutionResult result, IncludeFileState fileState, IncludeDirectives includeDirectives, List<FileVisitResult> included, CollectingMacroLookup dependentIncludeDirectives, Set<IncludeFile> includeFiles) {
            this.file = file;
            this.result = result;
            this.fileState = fileState;
            this.includeDirectives = includeDirectives;
            this.included = included;
            this.includeFileDirectives = dependentIncludeDirectives;
            this.includeFiles = includeFiles;
        }

        FileVisitResultImpl(File file) {
            this.file = file;
            result = IncludeFileResolutionResult.NoMacroIncludes;
            fileState = null;
            includeDirectives = null;
            included = Collections.emptyList();
            includeFileDirectives = null;
            includeFiles = Collections.emptySet();
        }

        @Override
        public boolean canReuse(ResolutionContext resolutionContext) {
            if (resolutionContext.canVisit(file)) {
                for (IncludeFile includeFile : includeFiles) {
                    if (!resolutionContext.canReuseResolutionResult(file, includeFile)) {
                        return false;
                    }
                }
                for (FileVisitResult result : included) {
                    if (!result.canReuse(resolutionContext)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public void collectDependencies(CollectingMacroLookup directives) {
            if (fileState != null) {
                directives.append(this);
            }
        }

        @Override
        public void collectFilesInto(Set<IncludeFileState> files) {
            if (fileState != null && files.contains(fileState)) {
                // Already seen during this traversal, skip
                return;
            }

            // Collect files
            if (fileState != null) {
                files.add(fileState);
                for (FileVisitResult include : included) {
                    include.collectFilesInto(files);
                }
            }
        }

        @Override
        public void collectInto(CollectingMacroLookup lookup) {
            if (fileState != null) {
                lookup.append(file, includeDirectives);
                includeFileDirectives.appendTo(lookup);
            }
        }

        @Override
        public IncludeFileResolutionResult getResult() {
            return result;
        }

        @Override
        public IncludeDirectives getIncludeDirectives() {
            return includeDirectives;
        }
    }
}
