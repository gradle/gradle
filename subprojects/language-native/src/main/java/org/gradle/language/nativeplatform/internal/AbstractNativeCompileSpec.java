/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.nativeplatform.internal.AbstractBinaryToolSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractNativeCompileSpec extends AbstractBinaryToolSpec implements NativeCompileSpec {

    private List<File> includeRoots = new ArrayList<File>();
    private List<File> systemIncludeRoots = new ArrayList<File>();
    private List<File> sourceFiles = new ArrayList<File>();
    private List<File> removedSourceFiles = new ArrayList<File>();
    private boolean incrementalCompile;
    private Map<String, String> macros = new LinkedHashMap<String, String>();
    private File objectFileDir;
    private boolean positionIndependentCode;
    private boolean debuggable;
    private boolean optimized;
    private BuildOperationLogger oplogger;
    private File prefixHeaderFile;
    private File preCompiledHeaderObjectFile;
    private List<File> sourceFilesForPch = new ArrayList<File>();
    private String preCompiledHeader;

    @Override
    public List<File> getIncludeRoots() {
        return includeRoots;
    }

    @Override
    public void include(File... includeRoots) {
        Collections.addAll(this.includeRoots, includeRoots);
    }

    @Override
    public void include(Iterable<File> includeRoots) {
        addAll(this.includeRoots, includeRoots);
    }

    @Override
    public List<File> getSystemIncludeRoots() {
        return systemIncludeRoots;
    }

    @Override
    public void systemInclude(Iterable<File> systemIncludeRoots) {
        addAll(this.systemIncludeRoots, systemIncludeRoots);
    }

    @Override
    public List<File> getSourceFiles() {
        return sourceFiles;
    }

    @Override
    public void source(Iterable<File> sources) {
        addAll(sourceFiles, sources);
    }

    @Override
    public void setSourceFiles(Collection<File> sources) {
        sourceFiles.clear();
        sourceFiles.addAll(sources);
    }

    @Override
    public List<File> getRemovedSourceFiles() {
        return removedSourceFiles;
    }

    @Override
    public void removedSource(Iterable<File> sources) {
        addAll(removedSourceFiles, sources);
    }

    @Override
    public void setRemovedSourceFiles(Collection<File> sources) {
        removedSourceFiles.clear();
        removedSourceFiles.addAll(sources);
    }

    @Override
    public boolean isIncrementalCompile() {
        return incrementalCompile;
    }

    @Override
    public void setIncrementalCompile(boolean flag) {
        incrementalCompile = flag;
    }

    @Override
    public File getObjectFileDir() {
        return objectFileDir;
    }

    @Override
    public void setObjectFileDir(File objectFileDir) {
        this.objectFileDir = objectFileDir;
    }

    @Override
    public Map<String, String> getMacros() {
        return macros;
    }

    @Override
    public void setMacros(Map<String, String> macros) {
        this.macros = macros;
    }

    @Override
    public void define(String name) {
        macros.put(name, null);
    }

    @Override
    public void define(String name, String value) {
        macros.put(name, value);
    }

    @Override
    public boolean isPositionIndependentCode() {
        return positionIndependentCode;
    }

    @Override
    public void setPositionIndependentCode(boolean positionIndependentCode) {
        this.positionIndependentCode = positionIndependentCode;
    }

    @Override
    public boolean isDebuggable() {
        return debuggable;
    }

    @Override
    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    @Override
    public boolean isOptimized() {
        return optimized;
    }

    @Override
    public void setOptimized(boolean optimized) {
        this.optimized = optimized;
    }

    @Override
    public File getPreCompiledHeaderObjectFile() {
        return preCompiledHeaderObjectFile;
    }

    @Override
    public void setPreCompiledHeaderObjectFile(File preCompiledHeaderObjectFile) {
        this.preCompiledHeaderObjectFile = preCompiledHeaderObjectFile;
    }

    @Override
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    @Override
    public void setPrefixHeaderFile(File pchFile) {
        this.prefixHeaderFile = pchFile;
    }

    @Override
    public String getPreCompiledHeader() {
        return preCompiledHeader;
    }

    @Override
    public void setPreCompiledHeader(String preCompiledHeader) {
        this.preCompiledHeader = preCompiledHeader;
    }

    private void addAll(List<File> list, Iterable<File> iterable) {
        for (File file : iterable) {
            list.add(file);
        }
    }

    @Override
    public BuildOperationLogger getOperationLogger() {
        return oplogger;
    }

    @Override
    public void setOperationLogger(BuildOperationLogger oplogger) {
        this.oplogger = oplogger;
    }

    @Override
    public List<File> getSourceFilesForPch() {
        return sourceFilesForPch;
    }

    @Override
    public void setSourceFilesForPch(List<File> sourceFilesForPch) {
        this.sourceFilesForPch = sourceFilesForPch;
    }
}
