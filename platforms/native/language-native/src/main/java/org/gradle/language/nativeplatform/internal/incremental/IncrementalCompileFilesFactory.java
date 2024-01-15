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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.vfs.FileSystemAccess;
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

    private final IncludeDirectives initialIncludeDirectives;
    private final SourceIncludesParser sourceIncludesParser;
    private final SourceIncludesResolver sourceIncludesResolver;
    private final FileSystemAccess fileSystemAccess;
    private final boolean ignoreUnresolvedHeadersInDependencies;

    public IncrementalCompileFilesFactory(IncludeDirectives initialIncludeDirectives, SourceIncludesParser sourceIncludesParser, SourceIncludesResolver sourceIncludesResolver, FileSystemAccess fileSystemAccess) {
        this.initialIncludeDirectives = initialIncludeDirectives;
        this.sourceIncludesParser = sourceIncludesParser;
        this.sourceIncludesResolver = sourceIncludesResolver;
        this.fileSystemAccess = fileSystemAccess;
        this.ignoreUnresolvedHeadersInDependencies = Boolean.getBoolean(IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME);
    }

    public IncrementalCompileSourceProcessor files(CompilationState previousCompileState) {
        return new DefaultIncrementalCompileSourceProcessor(previousCompileState);
    }

    private class DefaultIncrementalCompileSourceProcessor implements IncrementalCompileSourceProcessor {
        private final CompilationState previous;
        private final BuildableCompilationState current = new BuildableCompilationState();
        private final List<File> toRecompile = new ArrayList<File>();
        private final Set<File> existingHeaders = new HashSet<File>();
        private final Map<File, FileDetails> visitedFiles = new HashMap<File, FileDetails>();
        private boolean hasUnresolvedHeaders;

        DefaultIncrementalCompileSourceProcessor(CompilationState previousCompileState) {
            this.previous = previousCompileState == null ? new CompilationState() : previousCompileState;
        }

        @Override
        public IncrementalCompilation getResult() {
            return new DefaultIncrementalCompilation(current.snapshot(), toRecompile, getRemovedSources(), existingHeaders, hasUnresolvedHeaders);
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
            return fileSystemAccess.readRegularFileContentHash(sourceFile.getAbsolutePath())
                .map(fileContent -> {
                    SourceFileState previousState = previous.getState(sourceFile);

                    if (previousState != null) {
                        // Already seen this source file before. See if we can reuse the analysis from last time
                        if (graphHasNotChanged(sourceFile, fileContent, previousState, existingHeaders)) {
                            // Include file graph for this source file has not changed, skip this file
                            current.setState(sourceFile, previousState);
                            if (previousState.isHasUnresolved() && !ignoreUnresolvedHeadersInDependencies) {
                                hasUnresolvedHeaders = true;
                                return true;
                            }
                            return false;
                        }
                        // Else, something has changed in the include file graph for this source file, so analyse again
                    }

                    // Source file has not been compiled before, or its include file graph has changed in some way
                    // Calculate the include file graph for the source file and mark for recompilation

                    CollectingMacroLookup visibleMacros = new CollectingMacroLookup(initialIncludeDirectives);
                    FileVisitResult result = visitFile(sourceFile, fileContent, visibleMacros, new HashSet<HashCode>(), existingHeaders);
                    Set<IncludeFileEdge> includedFiles = new LinkedHashSet<IncludeFileEdge>();
                    result.collectFilesInto(includedFiles, new HashSet<File>());
                    SourceFileState newState = new SourceFileState(fileContent, result.result == IncludeFileResolutionResult.UnresolvedMacroIncludes, ImmutableSet.copyOf(includedFiles));
                    current.setState(sourceFile, newState);
                    if (newState.isHasUnresolved()) {
                        hasUnresolvedHeaders = true;
                    }
                    return true;
                })
                // Skip things that aren't files
                .orElse(false);
        }

        private boolean graphHasNotChanged(File sourceFile, HashCode fileHash, SourceFileState previousState, Set<File> existingHeaders) {
            if (!fileHash.equals(previousState.getHash())) {
                // Source file has changed
                return false;
            }
            if (previousState.getEdges().isEmpty()) {
                // Source file has not changed and no include files
                return true;
            }

            // Check each unique edge in the include file graph
            Map<HashCode, File> includes = new HashMap<HashCode, File>(previousState.getEdges().size());
            Set<File> headers = new HashSet<File>();
            includes.put(fileHash, sourceFile);
            for (IncludeFileEdge includeFileEdge : previousState.getEdges()) {
                File includedFrom = includeFileEdge.getIncludedBy() != null ? includes.get(includeFileEdge.getIncludedBy()) : null;
                SourceIncludesResolver.IncludeFile includeFile = sourceIncludesResolver.resolveInclude(includedFrom, includeFileEdge.getIncludePath());
                if (includeFile == null) {
                    // Include file not found (but previously was found)
                    return false;
                }
                HashCode hash = includeFile.getContentHash();
                if (!hash.equals(includeFileEdge.getResolvedTo())) {
                    // Include file changed
                    return false;
                }
                if (!existingHeaders.contains(includeFile.getFile())) {
                    // Collect for later, do not add until the graph is known to have not changed
                    headers.add(includeFile.getFile());
                }
                includes.put(hash, includeFile.getFile());
            }
            existingHeaders.addAll(headers);
            return true;
        }

        private FileVisitResult visitFile(File file, HashCode newHash, CollectingMacroLookup visibleMacros, Set<HashCode> visited, Set<File> existingHeaders) {
            FileDetails fileDetails = visitedFiles.get(file);
            if (fileDetails != null && fileDetails.results != null) {
                // A file that we can safely reuse the result for
                visibleMacros.append(fileDetails.results);
                return fileDetails.results;
            }

            if (!visited.add(newHash)) {
                // A cycle, treat as resolved here
                return new FileVisitResult(file);
            }

            if (fileDetails == null) {
                IncludeDirectives includeDirectives = sourceIncludesParser.parseIncludes(file);
                fileDetails = new FileDetails(includeDirectives);
                visitedFiles.put(file, fileDetails);
            }

            CollectingMacroLookup includedFileDirectives = new CollectingMacroLookup();
            visibleMacros.append(file, fileDetails.directives);

            List<Include> allIncludes = fileDetails.directives.getAll();
            List<FileVisitResult> included = allIncludes.isEmpty() ? Collections.<FileVisitResult>emptyList() : new ArrayList<FileVisitResult>(allIncludes.size());
            List<IncludeFileEdge> edges = allIncludes.isEmpty() ? Collections.<IncludeFileEdge>emptyList() : new ArrayList<IncludeFileEdge>(allIncludes.size());
            IncludeFileResolutionResult result = IncludeFileResolutionResult.NoMacroIncludes;
            for (Include include : allIncludes) {
                if (include.getType() == IncludeType.MACRO && result == IncludeFileResolutionResult.NoMacroIncludes) {
                    result = IncludeFileResolutionResult.HasMacroIncludes;
                }
                SourceIncludesResolver.IncludeResolutionResult resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleMacros);
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for '{}' in source file '{}'. Assuming changed.", include.getAsSourceText(), file.getName());
                    if (!ignoreUnresolvedHeadersInDependencies) {
                        result = IncludeFileResolutionResult.UnresolvedMacroIncludes;
                    }
                }
                for (SourceIncludesResolver.IncludeFile includeFile : resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile.getFile());
                    FileVisitResult includeVisitResult = visitFile(includeFile.getFile(), includeFile.getContentHash(), visibleMacros, visited, existingHeaders);
                    if (includeVisitResult.result.ordinal() > result.ordinal()) {
                        result = includeVisitResult.result;
                    }
                    includeVisitResult.collectDependencies(includedFileDirectives);
                    included.add(includeVisitResult);
                    edges.add(new IncludeFileEdge(includeFile.getPath(), includeFile.isQuotedInclude() ? newHash : null, includeFile.getContentHash()));
                }
            }

            FileVisitResult visitResult = new FileVisitResult(file, result, fileDetails.directives, included, edges, includedFileDirectives);
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
        final IncludeDirectives directives;
        // Non-null when the result of visiting this file can be reused
        @Nullable
        FileVisitResult results;

        FileDetails(IncludeDirectives directives) {
            this.directives = directives;
        }
    }

    /**
     * Details of a file included in a specific location in the file include graph.
     */
    private static class FileVisitResult implements CollectingMacroLookup.MacroSource {
        private final File file;
        private final IncludeFileResolutionResult result;
        private final IncludeDirectives includeDirectives;
        private final List<FileVisitResult> included;
        private final List<IncludeFileEdge> edges;
        private final CollectingMacroLookup includeFileDirectives;

        FileVisitResult(File file, IncludeFileResolutionResult result, IncludeDirectives includeDirectives, List<FileVisitResult> included, List<IncludeFileEdge> edges, CollectingMacroLookup dependentIncludeDirectives) {
            this.file = file;
            this.result = result;
            this.includeDirectives = includeDirectives;
            this.included = included;
            this.edges = edges;
            this.includeFileDirectives = dependentIncludeDirectives;
        }

        FileVisitResult(File file) {
            this.file = file;
            result = IncludeFileResolutionResult.NoMacroIncludes;
            includeDirectives = null;
            included = Collections.emptyList();
            edges = Collections.emptyList();
            includeFileDirectives = null;
        }

        void collectDependencies(CollectingMacroLookup directives) {
            if (includeDirectives != null) {
                directives.append(this);
            }
        }

        void collectFilesInto(Collection<IncludeFileEdge> files, Set<File> seen) {
            if (includeDirectives != null && seen.add(file)) {
                files.addAll(edges);
                for (FileVisitResult include : included) {
                    include.collectFilesInto(files, seen);
                }
            }
        }

        @Override
        public void collectInto(CollectingMacroLookup lookup) {
            if (includeDirectives != null) {
                lookup.append(file, includeDirectives);
                includeFileDirectives.appendTo(lookup);
            }
        }
    }
}
