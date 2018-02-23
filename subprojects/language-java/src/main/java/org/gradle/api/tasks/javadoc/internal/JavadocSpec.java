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

package org.gradle.api.tasks.javadoc.internal;

import org.gradle.external.javadoc.MinimalJavadocOptions;
import org.gradle.language.base.internal.compile.CompileSpec;

import java.io.File;

public class JavadocSpec implements CompileSpec {
    private MinimalJavadocOptions options;
    private boolean ignoreFailures;
    private File workingDir;
    private File optionsFile;
    private String executable;

    public void setOptions(MinimalJavadocOptions options) {
        this.options = options;
    }

    public MinimalJavadocOptions getOptions() {
        return options;
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir = workingDir;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public void setOptionsFile(File optionsFile) {
        this.optionsFile = optionsFile;
    }

    public File getOptionsFile() {
        return optionsFile;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public String getExecutable() {
        return executable;
    }
}
