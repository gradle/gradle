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

import org.gradle.api.file.FileCollection;

import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import org.gradle.api.GradleException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.gradle.api.plugins.antlr.internal.AntlrWorkerManager;
import org.gradle.api.plugins.antlr.internal.AntlrSpec;
import org.gradle.api.plugins.antlr.internal.AntlrResult;

import org.gradle.internal.Factory;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

/**
 * <p>Generates parsers from Antlr grammars.</p>
 *
 * <p>Most properties here are self-evident, but I wanted to highlight one in particular: {@link #setAntlrClasspath} is
 * used to define the classpath that should be passed along to the Ant {@link ANTLR} task as its classpath.  That is the
 * classpath it uses to perform generation execution.  This <b>should</b> really only require the antlr jar.  In {@link
 * AntlrPlugin} usage, this would happen simply by adding your antlr jar into the 'antlr' dependency configuration
 * created and exposed by the {@link AntlrPlugin} itself.</p>
 */
public class AntlrTask extends SourceTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrTask.class);

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;
    private String antlrVersion;

    private FileCollection antlrClasspath;

    private File outputDirectory;
    private File sourceDirectory;

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

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
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
     *
     * @param antlrClasspath The Ant task implementation classpath. Must not be null.
     */
    public void setAntlrClasspath(FileCollection antlrClasspath) {
        this.antlrClasspath = antlrClasspath;
    }

    @Inject
    public Factory<WorkerProcessBuilder> getWorkerProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void generate() {
        AntlrWorkerManager manager = new AntlrWorkerManager();

        // Build args
        List<String> args = new ArrayList<String>();
        args.add("-o");
        args.add(outputDirectory.getAbsolutePath());

        // Get files in source directory
        for (File file : sourceDirectory.listFiles()) {
            if (file.getName().endsWith(".g") || file.getName().endsWith(".g4")) {
                args.add(file.getAbsolutePath());
            }
        }

        AntlrSpec spec = new AntlrSpec(args);
        AntlrResult result = manager.runWorker(getProject().getProjectDir(), getWorkerProcessBuilderFactory(), antlrClasspath, spec);
        evaluateAntlrResult(result);
    }

    public void evaluateAntlrResult(AntlrResult result) {
        int errorCount = result.getErrorCount();
        if (errorCount == 1) {
            throw new GradleException("There was 1 error during grammar generation");
        } else if (errorCount > 1) {
            throw new GradleException("There were "
                + errorCount
                + " errors during grammar generation");
        }
    }
}
