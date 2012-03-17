/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Compilation options to be passed to the Groovy compiler.
 *
 * @author Hans Dockter
 */
public class GroovyCompileOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    /**
     * Tells whether the compilation task should fail if compile errors occurred. Defaults to <tt>true</tt>.
     */
    private boolean failOnError = true;

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Tells whether to turn on verbose output. Defaults to <tt>false</tt>.
     */
    private boolean verbose;

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Tells whether to print which source files are to be compiled. Defaults to <tt>false</tt>.
     */
    private boolean listFiles;

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    /**
     * The source encoding. Defaults to <tt>UTF-8</tt>.
     */
    @Input
    private String encoding = "UTF-8";

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Tells whether to run the Groovy compiler in a separate process. Defaults to <tt>true</tt>.
     */
    private boolean fork = true;

    public boolean isFork() {
        return fork;
    }

    public void setFork(boolean fork) {
        this.fork = fork;
    }

    /**
     * Options for running the Groovy compiler in a separate process. These options only take effect
     * if <tt>fork</tt> is set to <tt>true</tt>.
     */
    private GroovyForkOptions forkOptions = new GroovyForkOptions();

    public GroovyForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(GroovyForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    /**
     * Tells whether the Java runtime should be put on the compiler's compile class path. Defaults to <tt>false</tt>.
     */
    @Input
    private boolean includeJavaRuntime;

    public boolean isIncludeJavaRuntime() {
        return includeJavaRuntime;
    }

    public void setIncludeJavaRuntime(boolean includeJavaRuntime) {
        this.includeJavaRuntime = includeJavaRuntime;
    }

    /**
     * Tells whether to print a stack trace when the compiler hits a problem (like a compile error).
     * Defaults to <tt>false</tt>.
     */
    private boolean stacktrace;

    public boolean isStacktrace() {
        return stacktrace;
    }

    public void setStacktrace(boolean stacktrace) {
        this.stacktrace = stacktrace;
    }

    private boolean useAnt;

    public boolean isUseAnt() {
        return useAnt;
    }

    public void setUseAnt(boolean useAnt) {
        this.useAnt = useAnt;
    }

    private File stubDir;

    public File getStubDir() {
        return stubDir;
    }

    public void setStubDir(File stubDir) {
        this.stubDir = stubDir;
    }

    private boolean keepStubs;

    public boolean isKeepStubs() {
        return keepStubs;
    }

    public void setKeepStubs(boolean keepStubs) {
        this.keepStubs = keepStubs;
    }

    /**
     * Shortcut for setting both <tt>fork</tt> and <tt>forkOptions</tt>.
     *
     * @param forkArgs fork options in map notation
     */
    public GroovyCompileOptions fork(Map forkArgs) {
        fork = true;
        forkOptions.define(forkArgs);
        return this;
    }

    public List<String> excludedFieldsFromOptionMap() {
        return ImmutableList.of("forkOptions", "useAnt", "stubDir", "keepStubs");
    }

    public Map<String, String> fieldName2AntMap() {
        return ImmutableMap.of("failOnError", "failonerror", "listFiles", "listfiles");
    }

    public Map<String, Object> optionMap() {
        Map<String, Object> map = super.optionMap();
        map.putAll(forkOptions.optionMap());
        return map;
    }
}
