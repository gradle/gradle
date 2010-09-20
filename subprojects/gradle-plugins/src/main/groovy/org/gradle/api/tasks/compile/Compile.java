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

import org.gradle.api.AntBuilder;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.IncrementalJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompiler;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;

import java.io.File;

/**
 * Compiles Java source files.
 * 
 * @author Hans Dockter
 */
public class Compile extends AbstractCompile {

    private JavaCompiler javaCompiler;

    private File dependencyCacheDir;

    public Compile() {
        Factory<? extends AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        javaCompiler = new IncrementalJavaCompiler(new AntJavaCompiler((Factory) antBuilderFactory), antBuilderFactory, getOutputs());
    }

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

    @OutputDirectory
    public File getDependencyCacheDir() {
        return dependencyCacheDir;
    }

    public void setDependencyCacheDir(File dependencyCacheDir) {
        this.dependencyCacheDir = dependencyCacheDir;
    }

    @Nested
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
