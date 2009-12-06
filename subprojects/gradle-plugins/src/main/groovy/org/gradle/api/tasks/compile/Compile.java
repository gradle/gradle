/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GUtil;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class Compile extends SourceTask {

    private File destinationDir;

    private File dependencyCacheDir;

    private String sourceCompatibility;

    private String targetCompatibility;

    private FileCollection classpath;

    private CompileOptions options = new CompileOptions();

    protected AntJavac antCompile = new AntJavac();

    @TaskAction
    protected void compile() {
        if (antCompile == null) {
            throw new InvalidUserDataException("The ant compile command must be set!");
        }
        if (!GUtil.isTrue(getSourceCompatibility()) || !GUtil.isTrue(getTargetCompatibility())) {
            throw new InvalidUserDataException("The sourceCompatibility and targetCompatibility must be set!");
        }

        antCompile.execute(getSource(), getDestinationDir(), getDependencyCacheDir(), getClasspath(),
                getSourceCompatibility(), getTargetCompatibility(), options, getProject().getAnt());
        setDidWork(antCompile.getNumFilesCompiled() > 0);
    }

    @InputFiles
    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection configuration) {
        this.classpath = configuration;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @OutputDirectory
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getOptions() {
        return options;
    }

    public void setOptions(CompileOptions options) {
        this.options = options;
    }

    public void setAntCompile(AntJavac antCompile) {
        this.antCompile = antCompile;
    }
}
