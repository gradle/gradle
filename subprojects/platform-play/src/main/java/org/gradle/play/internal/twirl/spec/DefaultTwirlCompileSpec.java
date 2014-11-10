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

package org.gradle.play.internal.twirl.spec;

import java.io.File;

public abstract class DefaultTwirlCompileSpec implements TwirlCompileSpec {
    private final File sourceDirectory;
    private final Iterable<File> sources;
    private final boolean fork;
    private final boolean javaProject;
    private final File destinationDir;
    private final String additionalImports;
    private final String formatterType;
    private final Iterable<File> compileClasspath;

    public Iterable<File> getSources() {
        return sources;
    }

    public DefaultTwirlCompileSpec(File sourceDirectory, Iterable<File> sources, File destinationDir, Iterable<File> compileClasspath, boolean fork, boolean javaProject) {
        this(sourceDirectory, sources, destinationDir, null, null, compileClasspath, fork, javaProject);
    }

    public DefaultTwirlCompileSpec(File sourceDirectory, Iterable<File> sources, File destinationDir, String additionalImports, String formatterType, Iterable<File> compileClasspath, boolean fork, boolean javaProject) {
        this.sources = sources;
        this.destinationDir = destinationDir;
        this.sourceDirectory = sourceDirectory;
        this.compileClasspath = compileClasspath;
        this.fork = fork;
        this.javaProject = javaProject;
        if (additionalImports != null) {
            this.additionalImports = additionalImports;
        } else {
            String defaultFormat = "html";
            if (javaProject) {
                this.additionalImports = defaultJavaAdditionalImports(defaultFormat);
            } else {
                this.additionalImports = defaultScalaAdditionalImports(defaultFormat);
            }
        }
        if (formatterType != null) {
            this.formatterType = formatterType;
        } else {
            this.formatterType = defaultFormatterType();
        }
    }

    public File getDestinationDir(){
        return destinationDir;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public boolean isFork() {
        return fork;
    }

    protected abstract String defaultFormatterType();
    protected abstract String defaultJavaAdditionalImports(String format);
    protected abstract String defaultScalaAdditionalImports(String format);

    public String getFormatterType() {
        return this.formatterType;
    }

    public String getAdditionalImports() {
        return this.additionalImports;
    }


    public Iterable<File> getCompileClasspath() {
        return compileClasspath;
    }

    public boolean isJavaProject() {
        return javaProject;
    }
}
