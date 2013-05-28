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

package org.gradle.nativecode.language.cpp.internal;

import java.io.File;
import java.util.ArrayList;

public class DefaultCppCompileSpec implements CppCompileSpec {

    private Iterable<File> includeRoots;
    private Iterable<File> source;
    private Iterable<String> args = new ArrayList<String>();
    private File objectFileDir;
    private File tempDir;
    private boolean forDynamicLinking;

    public Iterable<File> getIncludeRoots() {
        return includeRoots;
    }

    public void setIncludeRoots(Iterable<File> includeRoots) {
        this.includeRoots = includeRoots;
    }

    public Iterable<File> getSource() {
        return source;
    }

    public void setSource(Iterable<File> source) {
        this.source = source;
    }

    public File getObjectFileDir() {
        return objectFileDir;
    }

    public void setObjectFileDir(File objectFileDir) {
        this.objectFileDir = objectFileDir;
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

    public boolean isForDynamicLinking() {
        return forDynamicLinking;
    }

    public void setForDynamicLinking(boolean forDynamicLinking) {
        this.forDynamicLinking = forDynamicLinking;
    }
}
