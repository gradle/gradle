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

package org.gradle.plugins.cpp.internal;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.ArrayList;

public class DefaultLinkerSpec implements LinkerSpec {

    private FileCollection libs;
    private FileCollection source;
    private File outputFile;
    private String installName;
    private File workDir;
    private Iterable<Object> args = new ArrayList<Object>();

    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source = source;
    }

    public FileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs = libs;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public File getWorkDir() {
        return workDir;
    }

    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }

    public void setArgs(Iterable<Object> args) {
        this.args = args;
    }

    public Iterable<Object> getArgs() {
        return args;
    }

    public String getInstallName() {
        return installName == null ? getOutputFile().getName() : installName;
    }

    public void setInstallName(String installName) {
        this.installName = installName;
    }
}
