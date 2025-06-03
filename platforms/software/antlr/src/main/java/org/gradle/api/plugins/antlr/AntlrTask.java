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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileType;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.antlr.internal.AntlrExecuter;
import org.gradle.api.plugins.antlr.internal.AntlrResult;
import org.gradle.api.plugins.antlr.internal.AntlrSourceGenerationException;
import org.gradle.api.plugins.antlr.internal.AntlrSpec;
import org.gradle.api.plugins.antlr.internal.AntlrSpecFactory;
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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.worker.MultiRequestClient;
import org.gradle.process.internal.worker.MultiRequestWorkerProcessBuilder;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Generates parsers from Antlr grammars.
 */
@NullMarked
@CacheableTask
public abstract class AntlrTask extends SourceTask {

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;
    private List<String> arguments = new ArrayList<>();

    ConfigurableFileCollection antlrClasspath = getProject().getObjects().fileCollection();

    private File outputDirectory;
    private String maxHeapSize;
    private FileCollection sourceSetDirectories;
    private final FileCollection stableSources = getProject().files((Callable<Object>) this::getSource);


    /**
     * Specifies that all rules call {@code traceIn}/{@code traceOut}.
     */
    @Input
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Returns the directory to generate the parser source files into.
     *
     * @return The output directory.
     */
    @OutputDirectory
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty(unreported = true, comment = "Setter has protected access")
    public FileCollection getAntlrClasspath() {
        return antlrClasspath;
    }

    /**
     * Specifies the classpath containing the Ant ANTLR task implementation.
     *
     * @param antlrClasspath The Ant task implementation classpath. Must not be null.
     */
    protected void setAntlrClasspath(FileCollection antlrClasspath) {
        this.antlrClasspath.setFrom(antlrClasspath);
    }

    @Inject
    protected abstract WorkerProcessFactory getWorkerProcessBuilderFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

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
                    throw UncheckedException.throwAsUncheckedException(ex);
                }
                grammarFiles.addAll(stableSources.getFiles());
            }
        } else {
            grammarFiles.addAll(stableSources.getFiles());
        }

        AntlrSpec spec = new AntlrSpecFactory().create(this, grammarFiles, sourceSetDirectories);
        MultiRequestClient<AntlrSpec, AntlrResult> client = getAntlrWorkerClient(spec);

        AntlrResult result;
        try {
            client.start();
            result = client.run(spec);
        } finally {
            client.stop();
        }

        evaluate(result);
    }

    private MultiRequestClient<AntlrSpec, AntlrResult> getAntlrWorkerClient(AntlrSpec spec) {
        MultiRequestWorkerProcessBuilder<AntlrSpec, AntlrResult> builder =
            getWorkerProcessBuilderFactory().multiRequestWorker(AntlrExecuter.class);

        builder.setBaseName("Gradle ANTLR Worker");
        builder.applicationClasspath(getAntlrClasspath());
        builder.sharedPackages("antlr", "org.antlr");

        JavaExecHandleBuilder javaCommand = builder.getJavaCommand();
        javaCommand.setWorkingDir(projectDir());
        javaCommand.setMaxHeapSize(spec.getMaxHeapSize());
        javaCommand.systemProperty("ANTLR_DO_NOT_EXIT", "true");
        javaCommand.redirectErrorStream();

        return builder.build();
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
    @ToBeReplacedByLazyProperty
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
    protected abstract Deleter getDeleter();
}
