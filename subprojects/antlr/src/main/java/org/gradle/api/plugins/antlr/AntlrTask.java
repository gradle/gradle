/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.antlr;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileType;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.antlr.internal.AntlrResult;
import org.gradle.api.plugins.antlr.internal.AntlrSourceGenerationException;
import org.gradle.api.plugins.antlr.internal.AntlrSpec;
import org.gradle.api.plugins.antlr.internal.AntlrSpecFactory;
import org.gradle.api.plugins.antlr.internal.AntlrWorkerManager;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.file.Deleter;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Generates parsers from Antlr grammars.
 */
@NonNullApi
@CacheableTask
public abstract class AntlrTask extends SourceTask {

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;
    private List<String> arguments = new ArrayList<>();

    private FileCollection antlrClasspath;

    private File outputDirectory;
    private String maxHeapSize;
    private FileCollection sourceSetDirectories;
    private final FileCollection stableSources = getProject().files((Callable<Object>) this::getSource);

    /**
     * Specifies that all rules call {@code traceIn}/{@code traceOut}.
     */
    @Input
    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    /**
     * Specifies that all lexer rules call {@code traceIn}/{@code traceOut}.
     */
    @Input
    public boolean isTraceLexer() {
        return traceLexer;
    }

    public void setTraceLexer(boolean traceLexer) {
        this.traceLexer = traceLexer;
    }

    /**
     * Specifies that all parser rules call {@code traceIn}/{@code traceOut}.
     */
    @Input
    public boolean isTraceParser() {
        return traceParser;
    }

    public void setTraceParser(boolean traceParser) {
        this.traceParser = traceParser;
    }

    /**
     * Specifies that all tree walker rules call {@code traceIn}/{@code traceOut}.
     */
    @Input
    public boolean isTraceTreeWalker() {
        return traceTreeWalker;
    }

    public void setTraceTreeWalker(boolean traceTreeWalker) {
        this.traceTreeWalker = traceTreeWalker;
    }

    /**
     * The maximum heap size for the forked antlr process (ex: '1g').
     */
    @Internal
    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public void setMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
    }

    public void setArguments(List<String> arguments) {
        if (arguments != null) {
            this.arguments = arguments;
        }
    }


    /**
     * List of command-line arguments passed to the antlr process
     *
     * @return The antlr command-line arguments
     */
    @Input
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Returns the directory to generate the parser source files into.
     *
     * @return The output directory.
     */
    @OutputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Specifies the directory to generate the parser source files into.
     *
     * @param outputDirectory The output directory. Must not be null.
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the classpath containing the Ant ANTLR task implementation.
     *
     * @return The Ant task implementation classpath.
     */
    @Classpath
    public FileCollection getAntlrClasspath() {
        return antlrClasspath;
    }

    /**
     * Specifies the classpath containing the Ant ANTLR task implementation.
     *
     * @param antlrClasspath The Ant task implementation classpath. Must not be null.
     */
    protected void setAntlrClasspath(FileCollection antlrClasspath) {
        this.antlrClasspath = antlrClasspath;
    }

    @Inject
    protected WorkerProcessFactory getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProjectLayout getProjectLayout() {
        throw new UnsupportedOperationException();
    }

    /**
     * Generate the parsers.
     *
     * @since 6.0
     */
    @TaskAction
    public void execute(InputChanges inputChanges) {
        Set<File> grammarFiles = new HashSet<>();
        FileCollection stableSources = getStableSources();
        if (inputChanges.isIncremental()) {
            boolean rebuildRequired = false;
            for (FileChange fileChange : inputChanges.getFileChanges(stableSources)) {
                if (fileChange.getFileType() == FileType.FILE) {
                    if (fileChange.getChangeType() == ChangeType.REMOVED) {
                        rebuildRequired = true;
                        break;
                    }
                    grammarFiles.add(fileChange.getFile());
                }
            }
            if (rebuildRequired) {
                try {
                    getDeleter().ensureEmptyDirectory(outputDirectory);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
                grammarFiles.addAll(stableSources.getFiles());
            }
        } else {
            grammarFiles.addAll(stableSources.getFiles());
        }

        AntlrWorkerManager manager = new AntlrWorkerManager();
        AntlrSpec spec = new AntlrSpecFactory().create(this, grammarFiles, sourceSetDirectories);
        AntlrResult result = manager.runWorker(projectDir(), getWorkerProcessBuilderFactory(), getAntlrClasspath(), spec);
        evaluate(result);
    }

    private void evaluate(AntlrResult result) {
        int errorCount = result.getErrorCount();
        if (errorCount < 0) {
            throw new AntlrSourceGenerationException("There were errors during grammar generation", result.getException());
        } else if (errorCount == 1) {
            throw new AntlrSourceGenerationException("There was 1 error during grammar generation", result.getException());
        } else if (errorCount > 1) {
            throw new AntlrSourceGenerationException("There were "
                + errorCount
                + " errors during grammar generation", result.getException());
        }
    }

    private File projectDir() {
        return getProjectLayout().getProjectDirectory().getAsFile();
    }

    /**
     * Sets the source for this task. Delegates to {@link #setSource(Object)}.
     *
     * If the source is of type {@link SourceDirectorySet}, then the relative path of each source grammar files
     * is used to determine the relative output path of the generated source
     * If the source is not of type {@link SourceDirectorySet}, then the generated source files end up
     * flattened in the specified output directory.
     *
     * @param source The source.
     * @since 4.0
     */
    @Override
    public void setSource(FileTree source) {
        setSource((Object) source);
    }

    /**
     * Sets the source for this task. Delegates to {@link SourceTask#setSource(Object)}.
     *
     * If the source is of type {@link SourceDirectorySet}, then the relative path of each source grammar files
     * is used to determine the relative output path of the generated source
     * If the source is not of type {@link SourceDirectorySet}, then the generated source files end up
     * flattened in the specified output directory.
     *
     * @param source The source.
     */
    @Override
    public void setSource(Object source) {
        super.setSource(source);
        if (source instanceof SourceDirectorySet) {
            this.sourceSetDirectories = ((SourceDirectorySet) source).getSourceDirectories();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal("tracked via stableSources")
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The sources for incremental change detection.
     *
     * @since 6.0
     */
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    protected FileCollection getStableSources() {
        return stableSources;
    }

    @Inject
    protected Deleter getDeleter() {
        throw new UnsupportedOperationException("Decorator takes care of injection");
    }
}
