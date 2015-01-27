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

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.antlr.internal.AntlrResult;
import org.gradle.api.plugins.antlr.internal.AntlrSourceGenerationException;
import org.gradle.api.plugins.antlr.internal.AntlrSpec;
import org.gradle.api.plugins.antlr.internal.AntlrWorkerManager;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.api.tasks.incremental.InputFileDetails;
import org.gradle.internal.Factory;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.GFileUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates parsers from Antlr grammars.
 */
public class AntlrTask extends SourceTask {

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;
    private List<String> arguments = new ArrayList<String>();

    private FileCollection antlrClasspath;

    private File outputDirectory;
    private String maxHeapSize;

    /**
     * Specifies that all rules call {@code traceIn}/{@code traceOut}.
     */
    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    /**
     * Specifies that all lexer rules call {@code traceIn}/{@code traceOut}.
     */
    public boolean isTraceLexer() {
        return traceLexer;
    }

    public void setTraceLexer(boolean traceLexer) {
        this.traceLexer = traceLexer;
    }

    /**
     * Specifies that all parser rules call {@code traceIn}/{@code traceOut}.
     */
    public boolean isTraceParser() {
        return traceParser;
    }

    public void setTraceParser(boolean traceParser) {
        this.traceParser = traceParser;
    }

    /**
     * Specifies that all tree walker rules call {@code traceIn}/{@code traceOut}.
     */
    public boolean isTraceTreeWalker() {
        return traceTreeWalker;
    }

    public void setTraceTreeWalker(boolean traceTreeWalker) {
        this.traceTreeWalker = traceTreeWalker;
    }

    /**
     * The maximum heap size for the forked antlr process (ex: '1g').
     */
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
    @InputFiles
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
    public Factory<WorkerProcessBuilder> getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void execute(IncrementalTaskInputs inputs) {
        final Set<File> grammarFiles = new HashSet<File>();
        final Set<File> sourceFiles = getSource().getFiles();
        final AtomicBoolean cleanRebuild = new AtomicBoolean();
        inputs.outOfDate(
                new Action<InputFileDetails>() {
                    public void execute(InputFileDetails details) {
                        File input = details.getFile();
                        if (sourceFiles.contains(input)) {
                            grammarFiles.add(input);
                        }else {
                            // classpath change?
                            cleanRebuild.set(true);
                        }
                    }
                }
        );
        inputs.removed(new Action<InputFileDetails>() {
            @Override
            public void execute(InputFileDetails details) {
                if (details.isRemoved()) {
                    cleanRebuild.set(true);
                }
            }
        });
        if (cleanRebuild.get()) {
            GFileUtils.cleanDirectory(outputDirectory);
            grammarFiles.addAll(sourceFiles);
        }
        List<String> args = buildArguments(grammarFiles);
        AntlrWorkerManager manager = new AntlrWorkerManager();
        AntlrSpec spec = new AntlrSpec(args, maxHeapSize);
        AntlrResult result = manager.runWorker(getProject().getProjectDir(), getWorkerProcessBuilderFactory(), getAntlrClasspath(), spec);
        evaluateAntlrResult(result);
    }

    public void evaluateAntlrResult(AntlrResult result) {
        int errorCount = result.getErrorCount();
        if (errorCount == 1) {
            throw new AntlrSourceGenerationException("There was 1 error during grammar generation", result.getException());
        } else if (errorCount > 1) {
            throw new AntlrSourceGenerationException("There were "
                    + errorCount
                    + " errors during grammar generation", result.getException());
        }
    }

    /**
     * Finalizes the list of arguments that will be sent to the ANTLR tool.
     */
    List<String> buildArguments(Set<File> grammarFiles) {
        List<String> args = new ArrayList<String>();    // List for finalized arguments

        // Output file
        args.add("-o");
        args.add(outputDirectory.getAbsolutePath());

        // Custom arguments
        for (String argument : arguments) {
            args.add(argument);
        }

        // Add trace parameters, if they don't already exist
        if (isTrace() && !arguments.contains("-trace")) {
            args.add("-trace");
        }
        if (isTraceLexer() && !arguments.contains("-traceLexer")) {
            args.add("-traceLexer");
        }
        if (isTraceParser() && !arguments.contains("-traceParser")) {
            args.add("-traceParser");
        }
        if (isTraceTreeWalker() && !arguments.contains("-traceTreeWalker")) {
            args.add("-traceTreeWalker");
        }

        // Files in source directory
        for (File file : grammarFiles) {
            args.add(file.getAbsolutePath());
        }

        return args;
    }
}
