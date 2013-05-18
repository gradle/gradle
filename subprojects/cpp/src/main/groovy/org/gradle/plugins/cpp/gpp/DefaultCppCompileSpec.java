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

package org.gradle.plugins.cpp.gpp;

import org.gradle.api.file.FileCollection;
import org.gradle.plugins.cpp.internal.CppCompileSpec;

import java.io.File;

/**
 * Stuff extracted out of GppCompileSpec that performs as a regular compile spec value object.
 * Values in here are set by the CompileTask: eventually everything should be in here.
 */
public abstract class DefaultCppCompileSpec implements CppCompileSpec {

    private FileCollection libs;
    private FileCollection includeRoots;
    private FileCollection source;
    private File outputFile;
    private File workDir;

    public FileCollection getIncludeRoots() {
        return includeRoots;
    }

    public void setIncludeRoots(FileCollection includeRoots) {
        this.includeRoots = includeRoots;
    }

    public FileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs = libs;
    }

    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }
}
