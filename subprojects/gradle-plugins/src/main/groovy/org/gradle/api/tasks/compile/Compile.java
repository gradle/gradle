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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.AntBuilderFactory;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.*;

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

    private JavaCompiler javaCompiler = new AntJavaCompiler(getServices().get(AntBuilderFactory.class));

    @TaskAction
    protected void compile() {
        javaCompiler.setSource(getSource());
        javaCompiler.setDestinationDir(getDestinationDir());
        javaCompiler.setClasspath(getClasspath());
        javaCompiler.setDependencyCacheDir(getDependencyCacheDir());
        javaCompiler.setSourceCompatibility(getSourceCompatibility());
        javaCompiler.setTargetCompatibility(getTargetCompatibility());
        WorkResult result = javaCompiler.execute();
        setDidWork(result.getDidWork());
    }

    @InputFiles
    public FileCollection getClasspath() {
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

    @Input
    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    public void setSourceCompatibility(String sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    @Input
    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(String targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public CompileOptions getOptions() {
        return javaCompiler.getCompileOptions();
    }

    public JavaCompiler getJavaCompiler() {
        return javaCompiler;
    }

    public void setJavaCompiler(JavaCompiler javaCompiler) {
        this.javaCompiler = javaCompiler;
    }
}
