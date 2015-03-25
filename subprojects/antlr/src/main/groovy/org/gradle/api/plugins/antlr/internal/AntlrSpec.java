/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.antlr.internal;

import java.io.File;
import java.io.Serializable;

import java.util.List;
import java.util.Set;

public class AntlrSpec implements Serializable {

    private List<String> arguments;
    private Set<File> grammarFiles;
    private String maxHeapSize;
    private File outputDirectory;

    private boolean trace;
    private boolean traceLexer;
    private boolean traceParser;
    private boolean traceTreeWalker;

    public AntlrSpec(List<String> arguments,
                     Set<File> grammarFiles,
                     File outputDirectory,
                     String maxHeapSize,
                     boolean trace,
                     boolean traceLexer,
                     boolean traceParser,
                     boolean traceTreeWalker) {
        this.arguments = arguments;
        this.grammarFiles = grammarFiles;
        this.outputDirectory = outputDirectory;
        this.maxHeapSize = maxHeapSize;
        this.trace = trace;
        this.traceLexer = traceLexer;
        this.traceParser = traceParser;
        this.traceTreeWalker = traceTreeWalker;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public Set<File> getGrammarFiles() {
        return grammarFiles;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public boolean isTrace() {
        return trace;
    }

    public boolean isTraceLexer() {
        return traceLexer;
    }

    public boolean isTraceParser() {
        return traceParser;
    }

    public boolean isTraceTreeWalker() {
        return traceTreeWalker;
    }
}
