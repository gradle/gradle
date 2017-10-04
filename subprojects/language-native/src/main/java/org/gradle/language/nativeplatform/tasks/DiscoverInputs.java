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

package org.gradle.language.nativeplatform.tasks;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.internal.changedetection.changes.IncrementalTaskInputsInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.cache.PersistentStateCache;
import org.gradle.internal.hash.FileHasher;
import org.gradle.language.nativeplatform.internal.incremental.CompilationState;
import org.gradle.language.nativeplatform.internal.incremental.CompilationStateCacheFactory;
import org.gradle.language.nativeplatform.internal.incremental.DefaultSourceIncludesParser;
import org.gradle.language.nativeplatform.internal.incremental.DefaultSourceIncludesResolver;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilation;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompileFilesFactory;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompileProcessor;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Responsible for discovering headers which are inputs to a native compilation task.
 *
 * @since 4.3
 */
@NonNullApi
@Incubating
public class DiscoverInputs extends DefaultTask {
    private final Logger logger = LoggerFactory.getLogger(DiscoverInputs.class);

    private final ConfigurableFileCollection includes;
    private final ConfigurableFileCollection source;
    private ImmutableList<String> includePaths;
    private PropertyState<Boolean> importsAreIncludes;
    private CSourceParser sourceParser;
    private final FileHasher hasher;
    private final CompilationStateCacheFactory compilationStateCacheFactory;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final RegularFileVar discoveredInputs;

    @Inject
    public DiscoverInputs(FileHasher hasher, CompilationStateCacheFactory compilationStateCacheFactory, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.hasher = hasher;
        this.compilationStateCacheFactory = compilationStateCacheFactory;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.includes = getProject().files();
        this.source = getProject().files();
        this.sourceParser = new RegexBackedCSourceParser();
        this.discoveredInputs = newOutputFile();
        this.importsAreIncludes = getProject().property(Boolean.class);
        dependsOn(includes);
    }

    @TaskAction
    public void discoverInputs(IncrementalTaskInputs incrementalTaskInputs) throws IOException {
        final IncrementalTaskInputsInternal inputs = (IncrementalTaskInputsInternal) incrementalTaskInputs;
        PersistentStateCache<CompilationState> compileStateCache = compilationStateCacheFactory.create(getPath());
        DefaultSourceIncludesParser sourceIncludesParser = new DefaultSourceIncludesParser(sourceParser, importsAreIncludes.getOrElse(false));
        List<File> includeRoots = CollectionUtils.toList(includes);
        DefaultSourceIncludesResolver dependencyParser = new DefaultSourceIncludesResolver(includeRoots);
        IncrementalCompileFilesFactory incrementalCompileFilesFactory = new IncrementalCompileFilesFactory(sourceIncludesParser, dependencyParser, hasher);
        IncrementalCompilation incrementalCompilation = new IncrementalCompileProcessor(compileStateCache, incrementalCompileFilesFactory).processSourceFiles(source.getFiles());
        File outputFile = getDiscoveredInputs().getAsFile().get();
        final BufferedWriter outputStreamWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), Charsets.UTF_8));
        try {
            for (File discoveredInput : incrementalCompilation.getDiscoveredInputs()) {
                addDiscoveredInput(inputs, outputStreamWriter, discoveredInput);
            }
            if (incrementalCompilation.isSourceFilesUseMacroIncludes()) {
                logger.info("After parsing the source files, Gradle cannot calculate the exact set of include files for {}. Every file in the include search path will be considered an input.", getName());
                for (final File includeRoot : includeRoots) {
                    logger.info("adding files in {} to discovered inputs for {}", includeRoot, getName());
                    directoryFileTreeFactory.create(includeRoot).visit(new EmptyFileVisitor() {
                        @Override
                        public void visitFile(FileVisitDetails fileDetails) {
                            try {
                                addDiscoveredInput(inputs, outputStreamWriter, fileDetails.getFile());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                }
            }
        } finally {
            IOUtils.closeQuietly(outputStreamWriter);
        }
    }

    private void addDiscoveredInput(IncrementalTaskInputsInternal inputs, BufferedWriter outputStreamWriter, File discoveredInput) throws IOException {
        inputs.newInput(discoveredInput);
        outputStreamWriter.write(discoveredInput.getAbsolutePath());
        outputStreamWriter.newLine();
    }

    @Input
    protected Collection<String> getIncludePaths() {
        if (includePaths == null) {
            Set<File> roots = includes.getFiles();
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (File root : roots) {
                builder.add(root.getAbsolutePath());
            }
            includePaths = builder.build();
        }
        return includePaths;
    }

    /**
     * Add directories where the compiler should search for header files.
     */
    public void includes(Object includeRoots) {
        includes.from(includeRoots);
    }

    /**
     * Returns the source files to be compiled.
     */
    @InputFiles
    @SkipWhenEmpty
    public ConfigurableFileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        source.from(sourceFiles);
    }

    @OutputFile
    public RegularFileVar getDiscoveredInputs() {
        return discoveredInputs;
    }

    @Input
    public boolean isImportsAreIncludes() {
        return importsAreIncludes.getOrElse(false);
    }

    public void setImportsAreIncludes(Provider<Boolean> importsAreIncludes) {
        this.importsAreIncludes.set(importsAreIncludes);
    }

}
