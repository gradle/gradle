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

import java.io.File;
import java.util.ArrayList;

public class DefaultLinkerSpec implements LinkerSpec {

    private Iterable<File> libs;
    private Iterable<File> source;
    private File outputFile;
    private String installName;
    private File workDir;
    private File tempDir;
    private Iterable<String> args = new ArrayList<String>();

    public Iterable<File> getSource() {
        return source;
    }

    public void setSource(Iterable<File> source) {
        this.source = source;
    }

    public Iterable<File> getLibs() {
        return libs;
    }

    public void setLibs(Iterable<File> libs) {
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

    public File getTempDir() {
        return tempDir;
    }

    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    public void setArgs(Iterable<String> args) {
        this.args = args;
    }

    public Iterable<String> getArgs() {
        return args;
    }

    public String getInstallName() {
        return installName == null ? getOutputFile().getName() : installName;
    }

    public void setInstallName(String installName) {
        this.installName = installName;
    }
}
